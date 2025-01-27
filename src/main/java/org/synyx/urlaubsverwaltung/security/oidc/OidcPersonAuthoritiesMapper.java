package org.synyx.urlaubsverwaltung.security.oidc;

import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;
import org.synyx.urlaubsverwaltung.person.Person;
import org.synyx.urlaubsverwaltung.person.PersonService;
import org.synyx.urlaubsverwaltung.person.Role;
import org.synyx.urlaubsverwaltung.security.PersonSyncService;

import java.util.Collection;
import java.util.Optional;

import static java.util.stream.Collectors.toList;
import static org.synyx.urlaubsverwaltung.person.Role.INACTIVE;

/**
 * @author Florian Krupicka - krupicka@synyx.de
 */
public class OidcPersonAuthoritiesMapper implements GrantedAuthoritiesMapper {

    private final PersonService personService;
    private final PersonSyncService personSyncService;

    public OidcPersonAuthoritiesMapper(PersonService personService, PersonSyncService personSyncService) {

        this.personService = personService;
        this.personSyncService = personSyncService;
    }

    @Override
    public Collection<? extends GrantedAuthority> mapAuthorities(Collection<? extends GrantedAuthority> authorities) {

        return authorities
            .stream()
            .filter(OidcUserAuthority.class::isInstance)
            .findFirst()
            .map(OidcUserAuthority.class::cast)
            .map(this::mapAuthorities)
            .orElseThrow(() -> new OidcPersonMappingException("oidc: The granted authority was not a 'OidcUserAuthority' and the user cannot be mapped."));
    }

    private Collection<? extends GrantedAuthority> mapAuthorities(OidcUserAuthority oidcUserAuthority) {

            final Optional<String> firstName = extractGivenName(oidcUserAuthority);
            final Optional<String> lastName = extractFamilyName(oidcUserAuthority);
            final Optional<String> mailAddress = extractMailAddress(oidcUserAuthority);

            final String userUniqueID = oidcUserAuthority.getIdToken().getSubject();

            final Optional<Person> maybePerson = personService.getPersonByLogin(userUniqueID);

            final Person person;

            if (maybePerson.isPresent()) {
                person = personSyncService.syncPerson(maybePerson.get(), firstName, lastName, mailAddress);

                if (person.hasRole(INACTIVE)) {
                    throw new DisabledException("User '" + person.getId() + "' has been deactivated");
                }

            } else {
                final Person createdPerson = personSyncService.createPerson(userUniqueID, firstName, lastName, mailAddress);
                person = personSyncService.appointAsOfficeUserIfNoOfficeUserPresent(createdPerson);
            }

            return person.getPermissions()
                .stream()
                .map(Role::name)
                .map(SimpleGrantedAuthority::new)
                .collect(toList());
    }


    private Optional<String> extractFamilyName(OidcUserAuthority authority) {

        final Optional<String> familyName = Optional.ofNullable(authority.getIdToken().getFamilyName());
        if (familyName.isPresent()) {
            return familyName;
        } else {
            return Optional.ofNullable(authority.getUserInfo().getFamilyName());
        }
    }

    private Optional<String> extractGivenName(OidcUserAuthority authority) {

        final Optional<String> givenName = Optional.ofNullable(authority.getIdToken().getGivenName());
        if (givenName.isPresent()) {
            return givenName;
        } else {
            return Optional.ofNullable(authority.getUserInfo().getGivenName());
        }

    }

    private Optional<String> extractMailAddress(OidcUserAuthority authority) {

        final Optional<String> mailAddress = Optional.ofNullable(authority.getIdToken().getEmail());
        if (mailAddress.isPresent()) {
            return mailAddress;
        } else {
            return Optional.ofNullable(authority.getUserInfo().getEmail());
        }
    }
}
