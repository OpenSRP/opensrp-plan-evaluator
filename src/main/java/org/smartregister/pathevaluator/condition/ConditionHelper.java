/**
 * 
 */
package org.smartregister.pathevaluator.condition;

import java.util.List;

import org.smartregister.domain.Action;
import org.smartregister.domain.Action.SubjectConcept;
import org.smartregister.domain.Condition;
import org.smartregister.pathevaluator.PathEvaluatorLibrary;
import org.smartregister.pathevaluator.action.ActionHelper;

import com.ibm.fhir.model.resource.Resource;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * @author Samuel Githengi created on 06/15/20
 */
@RequiredArgsConstructor
public class ConditionHelper {
	
	private PathEvaluatorLibrary pathEvaluatorLibrary = PathEvaluatorLibrary.getInstance();
	
	@NonNull
	private ActionHelper actionHelper;
	
	/**
	 * Evaluates an action conditions on whether task generation should be executed
	 * 
	 * @param resource the resource being evaluated against
	 * @param action the action being evaluated
	 * @return result of condition evaluation
	 */
	public boolean evaluateActionConditions(Resource resource, Action action) {
		boolean isValid = false;
		for (Condition condition : action.getConditions()) {
			SubjectConcept concept = condition.getExpression().getSubjectConcept();
			if (concept != null) {
				List<? extends Resource> resources = actionHelper.getSubjectResources(condition, concept.getText(),
				    resource);
				if (resources != null) {
					isValid = resources.stream().anyMatch(r -> pathEvaluatorLibrary.evaluateBooleanExpression(resource,
					    condition.getExpression().getExpression()));
				}
			} else {
				isValid = pathEvaluatorLibrary.evaluateBooleanExpression(resource,
				    condition.getExpression().getExpression());
			}
			if (isValid) {
				return isValid;
			}
		}
		return isValid;
	}
	
}
