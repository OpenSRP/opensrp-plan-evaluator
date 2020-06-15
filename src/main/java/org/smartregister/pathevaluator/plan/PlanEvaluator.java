package org.smartregister.pathevaluator.plan;

import java.util.Collection;
import java.util.List;

import org.smartregister.domain.Action;
import org.smartregister.domain.Jurisdiction;
import org.smartregister.domain.PlanDefinition;
import org.smartregister.pathevaluator.TriggerEvent;
import org.smartregister.pathevaluator.action.ActionHelper;
import org.smartregister.pathevaluator.condition.ConditionHelper;
import org.smartregister.pathevaluator.task.TaskHelper;
import org.smartregister.pathevaluator.utils.PlanHelper;

import com.ibm.fhir.model.resource.Encounter;
import com.ibm.fhir.model.resource.Resource;
import com.ibm.fhir.path.FHIRPathBooleanValue;
import com.ibm.fhir.path.FHIRPathNode;
import com.ibm.fhir.path.evaluator.FHIRPathEvaluator;

/**
 * 
 */

/**
 * @author Samuel Githengi created on 06/09/20
 */
public class PlanEvaluator {
	
	private FHIRPathEvaluator fhirPathEvaluator;
	
	private ActionHelper actionHelper;
	
	private ConditionHelper conditionHelper;
	
	private TaskHelper taskHelper;
	
	public PlanEvaluator() {
		fhirPathEvaluator = FHIRPathEvaluator.evaluator();
		actionHelper = new ActionHelper();
		conditionHelper = new ConditionHelper();
		taskHelper = new TaskHelper();
	}
	
	public boolean evaluateBooleanExpression(Resource resource, String expression) {
		
		try {
			Collection<FHIRPathNode> nodes = fhirPathEvaluator.evaluate(resource, expression);
			return nodes != null ? nodes.iterator().next().as(FHIRPathBooleanValue.class)._boolean() : false;
		}
		catch (Exception e) {
			return false;
		}
		
	}
	
	/**
	 * Evaluates plan after plan is saved on updated
	 * 
	 * @param planDefinition the new Plan definition
	 * @param existingPlanDefinition the existing plan definition
	 */
	public void evaluatePlan(PlanDefinition planDefinition, PlanDefinition existingPlanDefinition) {
		TriggerEvent triggerEvent = PlanHelper.evaluatePlanModification(planDefinition, existingPlanDefinition);
		if (triggerEvent != null) {//TODO implement the correct logic
			evaluatePlan(planDefinition, triggerEvent);
		}
		
	}
	
	/**
	 * Evaluates a plan if an encounter is submitted
	 * 
	 * @param planDefinition the plan being evaluated
	 * @param encounter the encounter that has just been submitted
	 */
	public void evaluatePlan(PlanDefinition planDefinition, Encounter encounter) {
	}
	
	/**
	 * Evaluates a plan for task generation
	 * 
	 * @param planDefinition the plan being evaluated
	 */
	private void evaluatePlan(PlanDefinition planDefinition, TriggerEvent triggerEvent) {
		for (Jurisdiction jurisdiction : planDefinition.getJurisdiction()) {
			evaluatePlan(planDefinition, triggerEvent, jurisdiction);
		}
	}
	
	/**
	 * Evaluates a plan for task generation
	 * 
	 * @param planDefinition the plan being evaluated
	 */
	private void evaluatePlan(PlanDefinition planDefinition, TriggerEvent triggerEvent, Jurisdiction jurisdiction) {
		
		for (Action action : planDefinition.getActions()) {
			
			//TODO @Ronald to add this
			//if (TriggerHelper.evaluateTrigger(action.getTrigger(), triggerEvent)) {
			
			//get the subject resources
			List<? extends Resource> resources = actionHelper.getSubjectResources(action, jurisdiction);
			
			for (Resource resource : resources) {
				if (conditionHelper.evaluateActionConditions(resource, action.getConditions())) {
					taskHelper.generateTask(resource, action);
				}
			}
			
		}
	}
	
}