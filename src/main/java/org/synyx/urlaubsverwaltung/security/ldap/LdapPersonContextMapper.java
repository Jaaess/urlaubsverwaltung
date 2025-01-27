package org.synyx.urlaubsverwaltung.security.ldap;

import org.slf4j.Logger;
import org.springframework.ldap.core.DirContextAdapter;
import org.springframework.ldap.core.DirContextOperations;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.ldap.userdetails.Person.Essence;
import org.springframework.security.ldap.userdetails.UserDetailsContextMapper;
import org.springframework.util.Assert;
import org.synyx.urlaubsverwaltung.person.Person;
import org.synyx.urlaubsverwaltung.person.PersonService;
import org.synyx.urlaubsverwaltung.person.Role;
import org.synyx.urlaubsverwaltung.security.PersonSyncService;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Optional;

import static java.lang.invoke.MethodHandles.lookup;
import static org.slf4j.LoggerFactory.getLogger;
import static org.synyx.urlaubsverwaltung.person.Role.INACTIVE;


/**
 * Map granted authorities to application roles described in {@link Role}.
 */
public class LdapPersonContextMapper implements UserDetailsContextMapper {

    private static final Logger LOG = getLogger(lookup().lookupClass());

    private final PersonService personService;
    private final PersonSyncService personSyncService;
    private final LdapUserMapper ldapUserMapper;

    LdapPersonContextMapper(PersonService personService, PersonSyncService personSyncService, LdapUserMapper ldapUserMapper) {
        this.personService = personService;
        this.personSyncService = personSyncService;
        this.ldapUserMapper = ldapUserMapper;
    }

    @Override
    public UserDetails mapUserFromContext(DirContextOperations ctx, String username, Collection<? extends GrantedAuthority> authorities) {

        final LdapUser ldapUser;

        try {
            ldapUser = ldapUserMapper.mapFromContext(ctx);
        } catch (InvalidSecurityConfigurationException | UnsupportedMemberAffiliationException ex) {
            throw new BadCredentialsException("No authentication possible for user = " + username, ex);
        }

        final String login = ldapUser.getUsername();
        final Optional<String> firstName = ldapUser.getFirstName();
        final Optional<String> lastName = ldapUser.getLastName();
        final Optional<String> email = ldapUser.getEmail();

        final Person person;

        final Optional<Person> maybePerson = personService.getPersonByLogin(login);
        if (maybePerson.isPresent()) {
            final Person existentPerson = maybePerson.get();

            if (existentPerson.hasRole(INACTIVE)) {
                LOG.info("User '{}' has been deactivated and can not sign in therefore", username);

                throw new DisabledException("User '" + username + "' has been deactivated");
            }

            person = personSyncService.syncPerson(existentPerson, firstName, lastName, email);
        } else {
            LOG.info("No user found for username '{}'", username);

            final Person createdPerson = personSyncService.createPerson(login, firstName, lastName, email);
            person = personSyncService.appointAsOfficeUserIfNoOfficeUserPresent(createdPerson);
        }

        /*
         * NOTE: If the system has no office user yet, grant office permissions to successfully signed in user
         */

        final Essence user = new Essence(ctx);
        user.setUsername(login);
        user.setAuthorities(getGrantedAuthorities(person));

        LOG.info("User '{}' has signed in with roles: {}", username, person.getPermissions());

        return user.createUserDetails();
    }


    /**
     * Gets the granted authorities using the {@link Role}s of the given {@link Person}.
     *
     * @param  person  to get the granted authorities for, may not be {@code null}
     *
     * @return  the granted authorities for the person
     */
    Collection<GrantedAuthority> getGrantedAuthorities(Person person) {

        Assert.notNull(person, "Person must be given.");

        Collection<Role> permissions = person.getPermissions();

        if (permissions.isEmpty()) {
            throw new IllegalStateException("Every user must have at least one role, data seems to be corrupt.");
        }

        Collection<GrantedAuthority> grantedAuthorities = new ArrayList<>();

        permissions.forEach(role -> grantedAuthorities.add(role::name));

        return grantedAuthorities;
    }


    @Override
    public void mapUserToContext(UserDetails user, DirContextAdapter ctx) {

        throw new UnsupportedOperationException("LdapPersonContextMapper only supports reading from a context. Please"
            + "use a subclass if mapUserToContext() is required.");
    }
}
