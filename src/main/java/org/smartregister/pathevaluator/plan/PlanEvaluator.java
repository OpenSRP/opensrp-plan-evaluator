package org.smartregister.pathevaluator.plan;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.smartregister.domain.Action;
import org.smartregister.domain.Jurisdiction;
import org.smartregister.domain.PlanDefinition;
import org.smartregister.pathevaluator.PathEvaluatorLibrary;
import org.smartregister.pathevaluator.TriggerEventPayload;
import org.smartregister.pathevaluator.TriggerType;
import org.smartregister.pathevaluator.action.ActionHelper;
import org.smartregister.pathevaluator.condition.ConditionHelper;
import org.smartregister.pathevaluator.dao.LocationDao;
import org.smartregister.pathevaluator.dao.PlanDao;
import org.smartregister.pathevaluator.dao.QueuingHelper;
import org.smartregister.pathevaluator.task.TaskHelper;
import org.smartregister.pathevaluator.trigger.TriggerHelper;

import com.ibm.fhir.model.resource.QuestionnaireResponse;
import com.ibm.fhir.model.resource.Resource;
import com.ibm.fhir.model.resource.Task;

/**
 * @author Samuel Githengi created on 06/09/20
 */
public class PlanEvaluator {
	
	private ActionHelper actionHelper;
	
	private ConditionHelper conditionHelper;
	
	private TaskHelper taskHelper;
	
	private TriggerHelper triggerHelper;
	
	private LocationDao locationDao;
	
	private String username;
	
	private QueuingHelper queuingHelper;
	
	private PlanDao planDao;
	
	public PlanEvaluator(String username) {
		this(username, null);
	}
	
	public PlanEvaluator(String username, QueuingHelper queuingHelper) {
		actionHelper = new ActionHelper();
		conditionHelper = new ConditionHelper(actionHelper);
		taskHelper = new TaskHelper();
		triggerHelper = new TriggerHelper(actionHelper);
		this.username = username;
		this.locationDao = PathEvaluatorLibrary.getInstance().getLocationProvider().getLocationDao();
		this.queuingHelper = queuingHelper;
		planDao = PathEvaluatorLibrary.getInstance().getPlanDao();
	}
	
	/**
	 * Evaluates plan after plan is saved on updated
	 *
	 * @param planDefinition the new Plan definition
	 * @param existingPlanDefinition the existing plan definition
	 */
	public void evaluatePlan(PlanDefinition planDefinition, PlanDefinition existingPlanDefinition) {
		TriggerEventPayload triggerEvent = PlanHelper.evaluatePlanModification(planDefinition, existingPlanDefinition);
		if (triggerEvent != null && (triggerEvent.getTriggerEvent().equals(TriggerType.PLAN_ACTIVATION)
		        || triggerEvent.getTriggerEvent().equals(TriggerType.PLAN_JURISDICTION_MODIFICATION))) {
			evaluatePlan(planDefinition, triggerEvent.getTriggerEvent(), triggerEvent.getJurisdictions());
		}
		
	}
	
	/**
	 * Evaluates a plan if an encounter is submitted
	 *
	 * @param planDefinition the plan being evaluated
	 * @param questionnaireResponse the questionnaireResponse that has just been submitted
	 */
	public void evaluatePlan(PlanDefinition planDefinition, QuestionnaireResponse questionnaireResponse) {
		QuestionnaireResponse.Item.Answer location = PathEvaluatorLibrary.getInstance()
		        .evaluateElementExpression(questionnaireResponse,
		            "QuestionnaireResponse.item.where(linkId='locationId').answer")
		        .element().as(QuestionnaireResponse.Item.Answer.class);
		
		evaluatePlan(planDefinition, TriggerType.EVENT_SUBMISSION,
		    new Jurisdiction(location.getValue().as(com.ibm.fhir.model.type.String.class).getValue()),
		    questionnaireResponse);
	}
	
	/**
	 * Evaluates a plan for task generation
	 *
	 * @param planDefinition the plan being evaluated
	 * @param triggerEvent
	 * @param jurisdictions
	 */
	private void evaluatePlan(PlanDefinition planDefinition, TriggerType triggerEvent, List<Jurisdiction> jurisdictions) {
		jurisdictions.parallelStream().forEach(jurisdiction -> {
			evaluatePlan(planDefinition, triggerEvent, jurisdiction, null);
			locationDao.findChildLocationByJurisdiction(jurisdiction.getCode()).parallelStream().forEach(
			    locationId -> queuingHelper.addToQueue(planDefinition.getIdentifier(), triggerEvent, locationId));
		});
	}
	
	/**
	 * Evaluates a plan for task generation
	 *
	 * @param planDefinition the plan being evaluated
	 * @param questionnaireResponse {@link QuestionnaireResponse} just submitted
	 */
	public void evaluatePlan(PlanDefinition planDefinition, TriggerType triggerEvent, Jurisdiction jurisdiction,
	        QuestionnaireResponse questionnaireResponse) {
		evaluatePlan(planDefinition, triggerEvent, jurisdiction, questionnaireResponse, true);
	}
	
	private void evaluatePlan(PlanDefinition planDefinition, TriggerType triggerEvent, Jurisdiction jurisdiction,
	        QuestionnaireResponse questionnaireResponse, boolean evaluateOtherPlans) {
		
		planDefinition.getActions().forEach(action -> {
			if (triggerHelper.evaluateTrigger(action.getTrigger(), triggerEvent, planDefinition.getIdentifier(),
			    questionnaireResponse)) {
				List<? extends Resource> resources;
				if (questionnaireResponse != null) {
					resources = actionHelper.getSubjectResources(action, questionnaireResponse,
					    planDefinition.getIdentifier());
				} else {
					resources = actionHelper.getSubjectResources(action, jurisdiction, planDefinition.getIdentifier());

				}
				List<String> otherPlans = new ArrayList<>();
				resources.forEach(resource -> {
					if (triggerEvent.equals(TriggerType.EVENT_SUBMISSION)) {
						evaluateResource(resource, questionnaireResponse, action, planDefinition.getIdentifier(),
						    jurisdiction.getCode(), triggerEvent);
						if (resource instanceof Task) {
							String planId = ((Task) resource).getBasedOn().get(0).getReference().getValue();
							if (evaluateOtherPlans && !planId.equals(planDefinition.getIdentifier())) {
								otherPlans.add(planId);
							}
						}
					} else {
						queuingHelper.addToQueue(resource.toString(), questionnaireResponse, action,
						    planDefinition.getIdentifier(), jurisdiction.getCode(), triggerEvent);
						
					}
				});
				evaluateOtherPlans(otherPlans, triggerEvent, jurisdiction, questionnaireResponse);
			}
		});
	}
	
	private void evaluateOtherPlans(List<String> otherPlans, TriggerType triggerEvent, Jurisdiction jurisdiction,
	        QuestionnaireResponse questionnaireResponse) {
		otherPlans.forEach(planId -> {
			PlanDefinition otherPlanDefinition = planDao.findPlanByIdentifier(planId);
			evaluatePlan(otherPlanDefinition, triggerEvent, jurisdiction, questionnaireResponse, false);
		});
	}
	
	public void evaluateResource(Resource resource, QuestionnaireResponse questionnaireResponse, Action action,
	        String planIdentifier, String jurisdictionCode, TriggerType triggerEvent) {
		if (conditionHelper.evaluateActionConditions(
		    questionnaireResponse == null ? resource
		            : questionnaireResponse.toBuilder().contained(Collections.singleton(resource)).build(),
		    action, planIdentifier, triggerEvent)) {
			if (action.getType().equals(Action.ActionType.UPDATE)) {
				taskHelper.updateTask(resource, action, questionnaireResponse);
			} else {
				taskHelper.generateTask(resource, action, planIdentifier, jurisdictionCode, username, questionnaireResponse);
			}
			
		}
	}
	
}
