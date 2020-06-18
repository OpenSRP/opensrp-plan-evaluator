package org.smartregister.converters;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.ibm.fhir.model.resource.Patient;
import org.joda.time.DateTime;
import org.junit.Test;
import org.smartregister.domain.Client;
import org.smartregister.utils.TaskDateTimeTypeConverter;

public class ClientConverterTest {

	private String CLIENT_JSON = "{\"firstName\":\"Khaleesi\",\"lastName\":\"Smith\",\"birthdate\":\"2000-11-21T02:00:00.000+02:00\",\"birthdateApprox\":false,\"deathdateApprox\":false,\"gender\":\"Female\",\"relationships\":{\"family\":[\"e6d3ea63-1309-4302-bd93-a6d0571cc645\"]},\"baseEntityId\":\"0587dcd6-4d8c-4540-be44-48706c0613db\",\"identifiers\":{\"opensrp_id\":\"20366639\"},\"addresses\":[],\"attributes\":{\"residence\":\"40930448-ea84-4ba7-8780-2a98dd8e902e\"},\"dateCreated\":\"2019-11-21T18:08:04.305+02:00\",\"serverVersion\":1574352482893,\"clientApplicationVersion\":7,\"clientDatabaseVersion\":3,\"type\":\"Client\",\"id\":\"a709a6b1-becf-4abd-91a7-dccaafe8714d\",\"revision\":\"v1\"}";

	private static Gson gson = new GsonBuilder().registerTypeAdapter(DateTime.class, new TaskDateTimeTypeConverter())
			.serializeNulls().create();

	@Test
	public void testConvertToPatientResource() {
		Client client = gson.fromJson(CLIENT_JSON, Client.class);
				client.setBirthdate(new DateTime(0l));
				client.setDeathdate(new DateTime(0l));
				client.setFirstName("John");
				client.setMiddleName("Lewis");
				client.setLastName("Johny");
				client.setGender("male");
		Patient patient = ClientConverter.convertClientToPatientResource(client);
		System.out.println(patient);
	}
}