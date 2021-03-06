package org.smartregister.domain;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.openpojo.reflection.PojoClass;
import com.openpojo.reflection.construct.InstanceFactory;
import org.junit.Test;

import com.openpojo.reflection.impl.PojoClassFactory;
import com.openpojo.validation.Validator;
import com.openpojo.validation.ValidatorBuilder;
import com.openpojo.validation.affirm.Affirm;
import com.openpojo.validation.test.impl.GetterTester;
import com.openpojo.validation.test.impl.SetterTester;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;
import org.smartregister.domain.User;

public class UserTest {
	
	@Test
	public void testEqualAndHashcodeContract() {
		EqualsVerifier.forClass(User.class).withIgnoredFields("id", "revision").suppress(Warning.NONFINAL_FIELDS)
		        .withPrefabValues(User.class, new User("ll"), new User("e")).withRedefinedSuperclass().verify();
	}
	
	@Test
	public void testGetterAndSetter() {
		Validator validator = ValidatorBuilder.create().with(new SetterTester()).with(new GetterTester()).build();
		
		validator.validate(PojoClassFactory.getPojoClass(User.class));
	}
	
	@Test
	public void testConstructor() {
		final Class<?> clazz = User.class;
		final Object obj1 = getInstance(clazz, "entityId", "username", "password", "salt");
		Affirm.affirmNotNull("Should have created an object", obj1);
		
		final Object obj2 = getInstance(clazz, new Object[] {});
		Affirm.affirmTrue("Should have created a different object", obj1 != obj2);
	}
	
	@Test
	public void testRoles() {
		User user = new User("dd", "userName", "password", "salt", "status", null, null);
		assertFalse(user.hasRole("role"));
		assertFalse(user.hasAdminRights());
		assertFalse(user.isDefaultAdmin());
		
		user.addRole("role");
		assertTrue(user.hasRole("role"));
		assertFalse(user.hasAdminRights());
		assertFalse(user.isDefaultAdmin());
		
		user.removeRole("role");
		assertFalse(user.hasRole("role"));
		
		user.withRole("admin");
		assertTrue(user.hasRole("admin"));
		assertTrue(user.hasAdminRights());
		assertFalse(user.isDefaultAdmin());
		
		user.removeRole("admin");
		user.withRole("administrator");
		assertFalse(user.hasRole("admin"));
		assertTrue(user.hasAdminRights());
		assertFalse(user.isDefaultAdmin());
		
		user.withUsername("admin");
		assertTrue(user.hasRole("administrator"));
		assertTrue(user.hasAdminRights());
		assertTrue(user.isDefaultAdmin());
	}
	
	@Test
	public void testPermission() {
		User user = new User();
		assertNull(user.getPermissions());
		assertFalse(user.hasPermission("permission"));
		
		user.withPermission("permission");
		assertNotNull(user.getPermissions());
		assertTrue(user.hasPermission("permission"));
		assertFalse(user.hasPermission("permis"));
		
		assertFalse(user.removePermission("perms"));
		
		assertTrue(user.removePermission("permission"));
		assertFalse(user.hasPermission("permission"));
		
		user.withPermissions(null);
		user.addPermission("permission");
		assertTrue(user.hasPermission("permission"));
	}

	public static Object getInstance(final Class<?> clazz, final Object... parameters) {
		final PojoClass pojoClass = PojoClassFactory.getPojoClass(clazz);
		return InstanceFactory.getInstance(pojoClass, parameters);
	}
	
}
