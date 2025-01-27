package org.synyx.urlaubsverwaltung.security.ldap;

import org.junit.Before;
import org.junit.Test;
import org.synyx.urlaubsverwaltung.person.Person;
import org.synyx.urlaubsverwaltung.person.PersonService;
import org.synyx.urlaubsverwaltung.security.PersonSyncService;
import org.synyx.urlaubsverwaltung.testdatacreator.TestDataCreator;

import java.util.Collections;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


public class LdapUserDataImporterTest {

    private LdapUserDataImporter ldapUserDataImporter;

    private LdapUserService ldapUserServiceMock;
    private PersonSyncService personSyncServiceMock;
    private PersonService personServiceMock;

    @Before
    public void setUp() {

        ldapUserServiceMock = mock(LdapUserService.class);
        personSyncServiceMock = mock(PersonSyncService.class);
        personServiceMock = mock(PersonService.class);

        ldapUserDataImporter = new LdapUserDataImporter(ldapUserServiceMock, personSyncServiceMock, personServiceMock);
    }


    @Test
    public void ensureFetchesLdapUsers() {

        ldapUserDataImporter.sync();

        verify(ldapUserServiceMock).getLdapUsers();
    }


    @Test
    public void ensureCreatesPersonIfLdapUserNotYetExists() {

        when(personServiceMock.getPersonByLogin(anyString())).thenReturn(Optional.empty());
        when(ldapUserServiceMock.getLdapUsers())
            .thenReturn(Collections.singletonList(
                    new LdapUser("muster", Optional.empty(), Optional.empty(), Optional.empty())));

        ldapUserDataImporter.sync();

        verify(personServiceMock, times(1)).getPersonByLogin("muster");
        verify(personSyncServiceMock)
            .createPerson("muster", Optional.empty(), Optional.empty(), Optional.empty());
    }


    @Test
    public void ensureUpdatesPersonIfLdapUserExists() {

        Person person = TestDataCreator.createPerson();

        when(personServiceMock.getPersonByLogin(anyString())).thenReturn(Optional.of(person));
        when(ldapUserServiceMock.getLdapUsers())
            .thenReturn(Collections.singletonList(
                    new LdapUser(person.getLoginName(), Optional.of("Vorname"), Optional.of("Nachname"),
                        Optional.of("Email"))));

        ldapUserDataImporter.sync();

        verify(personServiceMock, times(1)).getPersonByLogin(person.getLoginName());
        verify(personSyncServiceMock)
            .syncPerson(person, Optional.of("Vorname"), Optional.of("Nachname"), Optional.of("Email"));
    }
}
