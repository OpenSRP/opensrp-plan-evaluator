/**
 * 
 */
package org.smartregister.pathevaluator.dao;

import java.util.Collections;
import java.util.List;

import org.smartregister.pathevaluator.ResourceType;

import com.ibm.fhir.model.resource.Patient;
import com.ibm.fhir.model.resource.Resource;
import com.ibm.fhir.model.resource.Task;
import com.ibm.fhir.model.type.Identifier;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @author Samuel Githengi created on 06/18/20
 */
@AllArgsConstructor
@Getter
public class ClientProvider extends BaseProvider {
	
	public static final String FAMILY = "family";
	
	private ClientDao clientDao;
	
	public List<Patient> getFamilies(Resource resource, ResourceType fromResourceType) {
		switch (fromResourceType) {
			case JURISDICTION:
				return clientDao.findFamilyByJurisdiction(resource.getId());
			case LOCATION:
				return clientDao.findFamilyByResidence(resource.getId());
			case PERSON:
				Identifier familyId = getIdentifier((Patient) resource, FAMILY);
				if (familyId != null) {
					return clientDao.findClientById(familyId.getValue().getValue());
				}
				return null;
			case TASK:
				Task task = (Task) resource;
				return clientDao.findClientById(task.getFor().getReference().getValue());
			case QUESTIONAIRRE_RESPONSE:
				return clientDao.findClientById(resource.getId());
			case FAMILY:
				return Collections.singletonList((Patient)resource);
			default:
				return null;
		}
	}
	
	public List<Patient> getFamilyMembers(Resource resource, ResourceType fromResourceType) {
		switch (fromResourceType) {
			case JURISDICTION:
				return clientDao.findFamilyMemberyByJurisdiction(resource.getId());
			case LOCATION:
				return clientDao.findFamilyMemberByResidence(resource.getId());
			case FAMILY:
				return clientDao.findClientByRelationship(FAMILY, resource.getId());
			case TASK:
				Task task = (Task) resource;
				return clientDao.findClientById(task.getFor().getReference().getValue());
			case QUESTIONAIRRE_RESPONSE:
				return clientDao.findClientById(resource.getId());
			case PERSON:
				return Collections.singletonList((Patient)resource);
			default:
				return null;
		}
	}
	
}
