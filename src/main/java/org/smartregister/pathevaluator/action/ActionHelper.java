/**
 * 
 */
package org.smartregister.pathevaluator.action;

import com.ibm.fhir.model.resource.Patient;
import com.ibm.fhir.model.resource.QuestionnaireResponse;
import com.ibm.fhir.model.resource.Resource;
import com.ibm.fhir.path.FHIRPathElementNode;
import com.ibm.fhir.path.FHIRPathStringValue;
import org.smartregister.converters.TaskConverter;
import org.smartregister.domain.Action;
import org.smartregister.domain.Condition;
import org.smartregister.domain.Jurisdiction;
import org.smartregister.pathevaluator.PathEvaluatorLibrary;
import org.smartregister.pathevaluator.ResourceType;
import org.smartregister.pathevaluator.dao.ClientDao;
import org.smartregister.pathevaluator.dao.EventDao;
import org.smartregister.pathevaluator.dao.LocationDao;
import org.smartregister.pathevaluator.dao.TaskDao;

import org.smartregister.pathevaluator.dao.StockDao;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Samuel Githengi created on 06/15/20
 */
public class ActionHelper {
	
	private static Logger logger = Logger.getLogger(ActionHelper.class.getSimpleName());
	
	public static String RESIDENCE_EXPRESSION="$this.contained.identifier.where(id='residence' and type.coding.code ='attribute').value";

	private LocationDao locationDao = PathEvaluatorLibrary.getInstance().getLocationProvider().getLocationDao();
	
	private ClientDao clientDao = PathEvaluatorLibrary.getInstance().getClientProvider().getClientDao();

	private TaskDao taskDao = PathEvaluatorLibrary.getInstance().getTaskProvider().getTaskDao();

	private StockDao stockDao = PathEvaluatorLibrary.getInstance().getStockProvider().getStockDao();
	
	private EventDao eventDao = PathEvaluatorLibrary.getInstance().getEventProvider().getEventDao();

	/**
	 * Gets the resource type for the action
	 * 
	 * @param action the action
	 * @return the ResourceType for the action
	 */
	public static ResourceType getResourceType(Action action) {
		
		if (action.getSubjectCodableConcept() == null) {
			throw new IllegalArgumentException("getSubjectCodableConcept is null ");
		}
		return ResourceType.from(action.getSubjectCodableConcept().getText());
	}
	
	/**
	 * Gets the subject resources in the jurisdiction that tasks should be generated against
	 * 
	 * @param action
	 * @param jurisdiction
	 * @return resources that tasks should be generated against
	 */
	public List<? extends Resource> getSubjectResources(Action action, Jurisdiction jurisdiction,String planIdentifier) {
		ResourceType resourceType = getResourceType(action);
		switch (resourceType) {
			case JURISDICTION:
				
				return locationDao.findJurisdictionsById(jurisdiction.getCode());
			
			case LOCATION:
				return locationDao.findLocationByJurisdiction(jurisdiction.getCode());
			
			case FAMILY:
				return clientDao.findFamilyByJurisdiction(jurisdiction.getCode());
			
			case PERSON:
				return clientDao.findFamilyMemberyByJurisdiction(jurisdiction.getCode());

			case TASK:
			case GLOBAL_TASK:
				return taskDao.findTasksByJurisdiction(jurisdiction.getCode(),planIdentifier);

			case JURISDICTIONAL_TASK:
				return taskDao.findTasksByJurisdiction(jurisdiction.getCode());

			case DEVICE:
				return stockDao.findInventoryItemsInAJurisdiction(jurisdiction.getCode());
				
			case QUESTIONAIRRE_RESPONSE:
				return eventDao.findEventsByJurisdictionIdAndPlan(jurisdiction.getCode(), planIdentifier);

			case LOCATION_STOCK:
				return locationDao.findLocationAndStocksByJurisdiction(jurisdiction.getCode());

			default:
				logger.log(Level.WARNING,"unmapped resource type "+resourceType);
				return Collections.emptyList();
		}
	}
	
	/**
	 * Gets the subject resources for the questionnaireResponse that tasks should be generated
	 * against
	 * 
	 * @param action the action to evaluate
	 * @param questionnaireResponse the questionnaire being evaluated
	 * @return resources that tasks should be generated against
	 */
	public List<? extends Resource> getSubjectResources(Action action, QuestionnaireResponse questionnaireResponse, String planIdentifier) {
		ResourceType resourceType = getResourceType(action);
		String entity = questionnaireResponse.getSubject().getReference().getValue();
		switch (resourceType) {
			case JURISDICTION:
				return locationDao.findJurisdictionsById(entity);
			case LOCATION:
				return locationDao.findLocationsById(getLocationForQuestionnaire(questionnaireResponse, entity));
			case FAMILY:
			case PERSON:
				return clientDao.findClientById(entity);
			case TASK:
				FHIRPathStringValue taskIdentifierStringValue = PathEvaluatorLibrary.getInstance()
						.evaluateStringExpression(questionnaireResponse, "$this.item.where(linkId='taskIdentifier' and definition='details').answer.value.value");

				if (taskIdentifierStringValue != null) {
					org.smartregister.domain.Task task = taskDao.getTaskByIdentifier(taskIdentifierStringValue.string());
					if (task != null) {
						return Collections.singletonList(TaskConverter.convertTasktoFihrResource(task));
					}
				}

				return taskDao.findTasksForEntity(entity, planIdentifier);
			case DEVICE:
				String stockIdValue = questionnaireResponse != null && questionnaireResponse.getSubject() != null &&
						questionnaireResponse.getSubject().getReference() != null ?
						questionnaireResponse.getSubject().getReference().getValue() : "";

				if (stockIdValue != null) {
					return stockDao.getStockById(stockIdValue);
				}
				return stockDao.getStockById(entity);
				
			case GLOBAL_TASK:
				if (questionnaireResponse != null) {
					FHIRPathStringValue entityStringValue = PathEvaluatorLibrary.getInstance().evaluateStringExpression(
					    questionnaireResponse,
					    "$this.item.where(linkId='plan_evaluation_entity_id' and definition='details').answer.value.value");
					String entityId = entityStringValue != null ? entityStringValue.string()
					        : questionnaireResponse.getSubject().getReference().getValue();
					return PathEvaluatorLibrary.getInstance().getTaskProvider().getAllTasks(entityId);
				}

			case JURISDICTIONAL_TASK:
				// Jurisdictional.Task for EVENT_SUBMISSION trigger type has not been implemented since it is not in use
				// This is open for implementation and will not break any existing code
				logger.log(Level.WARNING,"unmapped resource type "+ resourceType);
				return Collections.emptyList();
				
			case QUESTIONAIRRE_RESPONSE:
				return eventDao.findEventsByEntityIdAndPlan(entity, planIdentifier);
			default:
				logger.log(Level.WARNING,"unmapped resource type "+ resourceType);
				return Collections.emptyList();
		}
	}
	
	private String getLocationForQuestionnaire(QuestionnaireResponse questionnaireResponse, String subject) {
		if (questionnaireResponse.getContained() != null && !questionnaireResponse.getContained().isEmpty()) {
			if (questionnaireResponse.getContained().get(0) instanceof Patient) {
				FHIRPathElementNode node = PathEvaluatorLibrary.getInstance()
				        .evaluateElementExpression(questionnaireResponse, ActionHelper.RESIDENCE_EXPRESSION);
				if (node != null) {
					return node.getValue().asStringValue().string();
				}
			}
		}
		return subject;
	}

	/**
	 * Gets the subject resources of the resource id that tasks should be generated against
	 *
	 * @param condition
	 * @param action
	 * @param id the resource id
	 * @return resources that tasks should be generated against
	 */
	public List<? extends Resource> getConditionSubjectResources(Condition condition, Action action, Resource resource,
	        String planIdentifier) {
		ResourceType conditionResourceType = ResourceType.from(condition.getExpression().getSubjectCodableConcept());
		ResourceType actionResourceType = ResourceType.from(action.getSubjectCodableConcept());
		if (resource instanceof QuestionnaireResponse) {
			conditionResourceType = ResourceType.QUESTIONAIRRE_RESPONSE;
		}
		return getConditionSubjectResources(resource, planIdentifier, conditionResourceType, actionResourceType);
	}
	
	/**
	 * Gets the subject resources for the resource
	 * 
	 * @param resource the resource id
	 * @param planIdentifier the plan Identifier
	 * @param conditionResourceType the condition/expression subject concept
	 * @param actionResourceType the action subject concept
	 * @return resources that tasks should be generated against
	 */
	public List<? extends Resource> getConditionSubjectResources(Resource resource, String planIdentifier,
	        ResourceType conditionResourceType, ResourceType actionResourceType) {
		switch (conditionResourceType) {
			case JURISDICTION:
				return PathEvaluatorLibrary.getInstance().getLocationProvider().getJurisdictions(resource,
				    actionResourceType);
			
			case LOCATION:
				return PathEvaluatorLibrary.getInstance().getLocationProvider().getLocations(resource, actionResourceType);
			
			case FAMILY:
				return PathEvaluatorLibrary.getInstance().getClientProvider().getFamilies(resource, actionResourceType);
			
			case PERSON:
				return PathEvaluatorLibrary.getInstance().getClientProvider().getFamilyMembers(resource, actionResourceType);
			
			case TASK:
				return PathEvaluatorLibrary.getInstance().getTaskProvider().getTasks(resource, planIdentifier);
			
			case QUESTIONAIRRE_RESPONSE:
				return PathEvaluatorLibrary.getInstance().getEventProvider().getEvents(resource, planIdentifier);

			case GLOBAL_TASK:
				return PathEvaluatorLibrary.getInstance().getTaskProvider().getAllTasks(resource.getId());

			case JURISDICTIONAL_TASK:
				// Jurisdictional.Task as a condition resource type for the action.condition.subjectCodableConcept has not been implemented since it is not in use
				// This is open for implementation and will not break any existing code
				logger.log(Level.WARNING,"unmapped resource type "+ conditionResourceType);
				return Collections.emptyList();

			case DEVICE:
				return PathEvaluatorLibrary.getInstance().getStockProvider().getStocksAgainstServicePointId(resource.getId()); //TODO
			default:
				logger.log(Level.WARNING,"unmapped resource type "+ conditionResourceType);
				return Collections.emptyList();
		}
	}
	
}
