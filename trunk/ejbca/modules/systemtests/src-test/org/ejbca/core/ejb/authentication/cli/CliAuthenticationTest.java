/*************************************************************************
 *                                                                       *
 *  EJBCA: The OpenSource Certificate Authority                          *
 *                                                                       *
 *  This software is free software; you can redistribute it and/or       *
 *  modify it under the terms of the GNU Lesser General Public           *
 *  License as published by the Free Software Foundation; either         *
 *  version 2.1 of the License, or any later version.                    *
 *                                                                       *
 *  See terms of license at gnu.org.                                     *
 *                                                                       *
 *************************************************************************/
package org.ejbca.core.ejb.authentication.cli;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.security.Principal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.ejb.RemoveException;
import javax.persistence.PersistenceException;

import org.cesecore.authentication.tokens.AuthenticationSubject;
import org.cesecore.authentication.tokens.UsernamePrincipal;
import org.cesecore.authorization.AuthorizationDeniedException;
import org.cesecore.authorization.control.AccessControlSessionRemote;
import org.cesecore.authorization.rules.AccessRuleData;
import org.cesecore.authorization.rules.AccessRuleState;
import org.cesecore.authorization.user.AccessMatchType;
import org.cesecore.authorization.user.AccessUserAspectData;
import org.cesecore.certificates.ca.CADoesntExistsException;
import org.cesecore.jndi.JndiHelper;
import org.cesecore.mock.authentication.tokens.TestAlwaysAllowLocalAuthenticationToken;
import org.cesecore.roles.RoleData;
import org.cesecore.roles.RoleNotFoundException;
import org.cesecore.roles.access.RoleAccessSessionRemote;
import org.cesecore.roles.management.RoleManagementSessionRemote;
import org.ejbca.core.EjbcaException;
import org.ejbca.core.ejb.config.ConfigurationSessionRemote;
import org.ejbca.core.ejb.ra.UserAdminSessionRemote;
import org.ejbca.core.model.approval.WaitingForApprovalException;
import org.ejbca.core.model.authorization.AccessRulesConstants;
import org.ejbca.core.model.ra.NotFoundException;
import org.ejbca.core.model.ra.raadmin.UserDoesntFullfillEndEntityProfile;
import org.ejbca.ui.cli.CliAuthenticationToken;
import org.ejbca.ui.cli.CliUserAccessMatchValue;
import org.ejbca.util.crypto.CryptoTools;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests in this class test aspect of CLI authentication.
 * 
 * Note that this test does not actually involve the CLI, it just tests the authentication 
 * that the CLI should use.
 * 
 * @version $Id$
 * 
 */
public class CliAuthenticationTest {

    private static final String CLI_TEST_ROLENAME = "CLI_TEST_ROLENAME";

    private final AccessControlSessionRemote accessControlSession = JndiHelper.getRemoteSession(AccessControlSessionRemote.class);
    private final CliAuthenticationProviderRemote cliAuthenticationProvider = JndiHelper.getRemoteSession(CliAuthenticationProviderRemote.class);
    private final ConfigurationSessionRemote configurationSession = JndiHelper.getRemoteSession(ConfigurationSessionRemote.class);
    private final RoleAccessSessionRemote roleAccessSession = JndiHelper.getRemoteSession(RoleAccessSessionRemote.class);
    private final RoleManagementSessionRemote roleManagementSession = JndiHelper.getRemoteSession(RoleManagementSessionRemote.class);
    private final UserAdminSessionRemote userAdminSessionRemote = JndiHelper.getRemoteSession(UserAdminSessionRemote.class);    

    private CliAuthenticationTestHelperSessionRemote cliAuthenticationTestHelperSession = JndiHelper
            .getRemoteSession(CliAuthenticationTestHelperSessionRemote.class);

    private final TestAlwaysAllowLocalAuthenticationToken internalToken = new TestAlwaysAllowLocalAuthenticationToken(new UsernamePrincipal(
            CliAuthenticationProviderRemote.class.getSimpleName()));

    @Before
    public void setUp() throws Exception {        
        RoleData role = roleAccessSession.findRole(CLI_TEST_ROLENAME);
        if(role == null) {
            role = roleManagementSession.create(internalToken, CLI_TEST_ROLENAME);
        }
        List<AccessUserAspectData> subjects = new ArrayList<AccessUserAspectData>();
        AccessUserAspectData defaultCliUserAspect = new AccessUserAspectData(CLI_TEST_ROLENAME, 0, CliUserAccessMatchValue.USERNAME,
                AccessMatchType.TYPE_EQUALCASE, CliAuthenticationTestHelperSessionRemote.USERNAME);
        subjects.add(defaultCliUserAspect);
        roleManagementSession.addSubjectsToRole(internalToken, role, subjects);

        AccessRuleData rule = new AccessRuleData(CLI_TEST_ROLENAME, AccessRulesConstants.ROLE_ROOT, AccessRuleState.RULE_ACCEPT, true);
        List<AccessRuleData> newrules = new ArrayList<AccessRuleData>();
        newrules.add(rule);
        roleManagementSession.addAccessRulesToRole(internalToken, role, newrules);       
    }

    @After
    public void tearDown() throws Exception {
        try {
            userAdminSessionRemote.deleteUser(internalToken, CliAuthenticationTestHelperSessionRemote.USERNAME);
        } catch (NotFoundException e) {
            // NOPMD
        }
        try {
            roleManagementSession.remove(internalToken, CLI_TEST_ROLENAME);
        } catch(RoleNotFoundException e) {
         // NOPMD
        }
        configurationSession.restoreConfiguration();
    }

    @Test
    public void testInstallCliAuthenticationWithBCrypt() throws PersistenceException, CADoesntExistsException, AuthorizationDeniedException,
            UserDoesntFullfillEndEntityProfile, WaitingForApprovalException, EjbcaException, RemoveException {
        cliAuthenticationTestHelperSession.createUser(CliAuthenticationTestHelperSessionRemote.USERNAME, CliAuthenticationTestHelperSessionRemote.PASSWORD);
        Set<Principal> principals = new HashSet<Principal>();
        principals.add(new UsernamePrincipal(CliAuthenticationTestHelperSessionRemote.USERNAME));
        AuthenticationSubject subject = new AuthenticationSubject(principals, null);
        CliAuthenticationToken authenticationToken =  (CliAuthenticationToken) cliAuthenticationProvider.authenticate(subject);
        // Set hashed value anew in order to send back
        authenticationToken.setSha1HashFromCleartextPassword(CliAuthenticationTestHelperSessionRemote.PASSWORD);
        assertTrue(accessControlSession.isAuthorized(authenticationToken, AccessRulesConstants.ROLE_ROOT));
    }

    @Test
    public void testInstallCliAuthenticationWithOldHash() {        
        configurationSession.updateProperty("ejbca.passwordlogrounds", "0");
        cliAuthenticationTestHelperSession.createUser(CliAuthenticationTestHelperSessionRemote.USERNAME, CliAuthenticationTestHelperSessionRemote.PASSWORD);
        Set<Principal> principals = new HashSet<Principal>();
        principals.add(new UsernamePrincipal(CliAuthenticationTestHelperSessionRemote.USERNAME));
        AuthenticationSubject subject = new AuthenticationSubject(principals, null);
        CliAuthenticationToken authenticationToken = (CliAuthenticationToken) cliAuthenticationProvider.authenticate(subject);
        // Set hashed value anew in order to send back
        authenticationToken.setSha1HashFromCleartextPassword(CliAuthenticationTestHelperSessionRemote.PASSWORD);
        assertFalse("Old-style hash value was not used (BCrypt prefix detected).", authenticationToken.getSha1Hash().startsWith(CryptoTools.BCRYPT_PREFIX));
        assertTrue(accessControlSession.isAuthorized(authenticationToken, AccessRulesConstants.ROLE_ROOT));
    }
    
    /**
     * This test tests CLI Authentication failure as per the Common Criteria standard:
     * 
     *    FIA_UAU.1 Timing of authentication
     *    Unsuccessful use of the authentication mechanism
     *    
     *    FIA_UID.1 Timing of identification
     *    Unsuccessful use of the user identification mechanism, including the
     *    user identity provided 
     */
    @Test
    public void testAuthenticationFailureDueToNonExistingUser() {
        Set<Principal> principals = new HashSet<Principal>();
        principals.add(new UsernamePrincipal(CliAuthenticationTestHelperSessionRemote.USERNAME));
        AuthenticationSubject subject = new AuthenticationSubject(principals, null);
        CliAuthenticationToken authenticationToken = (CliAuthenticationToken) cliAuthenticationProvider.authenticate(subject);
        assertNull("Authentication token was returned for nonexisting user", authenticationToken);
        //TODO: Examine the logs
     //   securityEventsAuditorSession.selectAuditLogs(internalToken, startIndex, max, criteria, logDeviceId)
    }

}
