/**
 * 
 */
package org.smartregister.pathevaluator.plan;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;
import org.powermock.reflect.Whitebox;
import org.smartregister.domain.Action;
import org.smartregister.domain.Jurisdiction;
import org.smartregister.domain.PlanDefinition;
import org.smartregister.domain.PlanDefinition.PlanStatus;
import org.smartregister.pathevaluator.PathEvaluatorLibrary;
import org.smartregister.pathevaluator.TestData;
import org.smartregister.pathevaluator.TriggerType;
import org.smartregister.pathevaluator.action.ActionHelper;
import org.smartregister.pathevaluator.condition.ConditionHelper;
import org.smartregister.pathevaluator.dao.LocationDao;
import org.smartregister.pathevaluator.dao.QueuingHelper;
import org.smartregister.pathevaluator.task.TaskHelper;
import org.smartregister.pathevaluator.trigger.TriggerHelper;

import com.ibm.fhir.model.resource.Patient;
import com.ibm.fhir.model.resource.QuestionnaireResponse;

/**
 * @author Samuel Githengi created on 06/15/20
 */
@RunWith(MockitoJUnitRunner.class)
public class PlanEvaluatorTest {
	
	private PlanEvaluator planEvaluator;
	
	@Mock
	private ActionHelper actionHelper;
	
	@Mock
	private TriggerHelper triggerHelper;
	
	@Mock
	private ConditionHelper conditionHelper;
	
	@Mock
	private TaskHelper taskHelper;

	@Mock
	private LocationDao locationDao;

	@Mock
	private QueuingHelper queuingHelper;
	
	@Captor
	private ArgumentCaptor<QuestionnaireResponse> questionnaireCaptor;
	
	private String plan = UUID.randomUUID().toString();
	
	private String username = UUID.randomUUID().toString();

	@Before
	public void setUp() {
		PathEvaluatorLibrary.init(null, null, null, null, null);
		planEvaluator = new PlanEvaluator(username);
		Whitebox.setInternalState(planEvaluator, "actionHelper", actionHelper);
		Whitebox.setInternalState(planEvaluator, "conditionHelper", conditionHelper);
		Whitebox.setInternalState(planEvaluator, "taskHelper", taskHelper);
		Whitebox.setInternalState(planEvaluator, "triggerHelper", triggerHelper);
		Whitebox.setInternalState(planEvaluator, "locationDao", locationDao);
		Whitebox.setInternalState(planEvaluator, "queuingHelper", queuingHelper);
	}
	
	@Test
	public void testEvaluatePlanForInactivePlan() {
		PlanDefinition planDefinition = TestData.createPlan();
		PlanDefinition planDefinition2 = null;
		planEvaluator.evaluatePlan(planDefinition, planDefinition2);
		verify(actionHelper, never()).getSubjectResources(any(), any(Jurisdiction.class),eq(planDefinition.getIdentifier()));
	}
	
	@Test
	public void testEvaluatePlanThroughQueue() {
		PlanDefinition planDefinition = TestData.createPlan();
		List<String> jurisdictionList = new ArrayList<>();
		jurisdictionList.add("jurisdiction");
		planDefinition.setIdentifier(plan);
		planDefinition.setStatus(PlanStatus.ACTIVE);
		PlanDefinition planDefinition2 = null;
		List<Patient> patients = Collections.singletonList(TestData.createPatient());
		Action action = planDefinition.getActions().get(0);
		Jurisdiction jurisdiction = planDefinition.getJurisdiction().get(0);
		when(actionHelper.getSubjectResources(action, jurisdiction,planDefinition.getIdentifier())).thenAnswer(new Answer<List<Patient>>() {
			
			@Override
			public List<Patient> answer(InvocationOnMock invocation) throws Throwable {
				return patients;
			}
		});
		when(triggerHelper.evaluateTrigger(action.getTrigger(), TriggerType.PLAN_ACTIVATION, plan, null)).thenReturn(true);
		when(locationDao.findChildLocationByJurisdiction(anyString())).thenReturn(jurisdictionList);
		Mockito.doNothing().when(queuingHelper).addToQueue(anyString(),any(TriggerType.class),anyString(),eq(username));
		planEvaluator.evaluatePlan(planDefinition, planDefinition2);
		int evaluations = planDefinition.getActions().size() * planDefinition.getJurisdiction().size();
		verify(triggerHelper, times(evaluations)).evaluateTrigger(action.getTrigger(), TriggerType.PLAN_ACTIVATION, plan,
		    null);
		verify(actionHelper, times(evaluations)).getSubjectResources(any(), any(Jurisdiction.class),eq(planDefinition.getIdentifier()));
		verify(queuingHelper,times(1)).addToQueue(anyString(),any(TriggerType.class),anyString(),eq(username));
	}
	
	@Test
	public void testEvaluatePlanWithQuestionnaireEvaluatesConditions() {
		PlanDefinition planDefinition = TestData.createPlan();
		planDefinition.setIdentifier(plan);
		planDefinition.setStatus(PlanStatus.ACTIVE);
		List<String> jurisdictionList = new ArrayList<>();
		jurisdictionList.add("jurisdiction");
		
		QuestionnaireResponse questionnaire = TestData.createResponse();
		List<Patient> patients = Collections.singletonList(TestData.createPatient());
		Action action = planDefinition.getActions().get(0);
		String entity = questionnaire.getSubject().getReference().getValue();
		when(actionHelper.getSubjectResources(action, questionnaire, planDefinition.getIdentifier())).thenAnswer(new Answer<List<Patient>>() {
			
			@Override
			public List<Patient> answer(InvocationOnMock invocation) throws Throwable {
				return patients;
			}
		});
		when(conditionHelper.evaluateActionConditions(any(), eq(action), eq(plan), eq(TriggerType.EVENT_SUBMISSION)))
		        .thenReturn(true);
		when(triggerHelper.evaluateTrigger(eq(action.getTrigger()), eq(TriggerType.EVENT_SUBMISSION), eq(plan),
		    any(QuestionnaireResponse.class))).thenReturn(true);
		planEvaluator.evaluatePlan(planDefinition, questionnaire);
		int evaluations = planDefinition.getActions().size() * planDefinition.getJurisdiction().size();
		verify(triggerHelper, times(evaluations)).evaluateTrigger(action.getTrigger(), TriggerType.EVENT_SUBMISSION, plan,
		    questionnaire);
		verify(actionHelper).getSubjectResources(action, questionnaire, planDefinition.getIdentifier());
		
		verify(conditionHelper).evaluateActionConditions(questionnaireCaptor.capture(), eq(action), eq(plan),
		    eq(TriggerType.EVENT_SUBMISSION));
		
		verify(taskHelper).generateTask(patients.get(0), action, planDefinition.getIdentifier(), "123343430mmmj", username,
		    questionnaire);
		
		QuestionnaireResponse actual = questionnaireCaptor.getValue();
		assertEquals(questionnaire.getId(), actual.getId());
		assertEquals(questionnaire.getItem().size(), actual.getItem().size());
		assertEquals(patients.get(0), actual.getContained().get(0));
		
	}
	
}
