/*************************************************************************
 *                                                                       *
 *  EJBCA - Proprietary Modules: Enterprise Certificate Authority        *
 *                                                                       *
 *  Copyright (c), PrimeKey Solutions AB. All rights reserved.           *
 *  The use of the Proprietary Modules are subject to specific           * 
 *  commercial license terms.                                            *
 *                                                                       *
 *************************************************************************/
package org.ejbca.core.model.era;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.log4j.Logger;
import org.cesecore.authentication.AuthenticationFailedException;
import org.cesecore.authentication.tokens.AuthenticationToken;
import org.cesecore.authorization.AuthorizationDeniedException;
import org.cesecore.authorization.access.AccessSet;
import org.cesecore.authorization.control.AccessControlSessionLocal;
import org.cesecore.certificates.ca.CAConstants;
import org.cesecore.certificates.ca.CADoesntExistsException;
import org.cesecore.certificates.ca.CaSessionLocal;
import org.cesecore.certificates.endentity.EndEntityInformation;
import org.ejbca.core.EjbcaException;
import org.ejbca.core.ejb.ra.EndEntityAccessSessionLocal;
import org.ejbca.core.model.util.EjbLocalHelper;

/**
 * Implementation of the RaMasterApi that invokes functions at the local node.
 * 
 * @version $Id$
 */
public class RaMasterApiLocalImpl implements RaMasterApi {
    
    private static final Logger log = Logger.getLogger(RaMasterApiLocalImpl.class);

    private final AccessControlSessionLocal accessControlSession;
    private final CaSessionLocal caSession;
    private final EndEntityAccessSessionLocal endEntityAccessSession;
    private Boolean backendAvailable = null;
    
    public RaMasterApiLocalImpl() {
        final EjbLocalHelper ejb = new EjbLocalHelper();
        accessControlSession = ejb.getAccessControlSession();
        endEntityAccessSession = ejb.getEndEntityAccessSession();
        caSession = ejb.getCaSession();
    }

    @Override
    public boolean isBackendAvailable() {
        if (backendAvailable==null) {
            boolean available = false;
            for (int caId : caSession.getAllCaIds()) {
                try {
                    if (caSession.getCAInfoInternal(caId).getStatus() == CAConstants.CA_ACTIVE) {
                        available = true;
                        break;
                    }
                } catch (CADoesntExistsException e) {
                    log.debug("Fail to get existing CA's info. " + e.getMessage());
                }
            }
            backendAvailable = Boolean.valueOf(available);
        }
        return backendAvailable.booleanValue();
    }
    
    @Override
    public AccessSet getUserAccessSet(final AuthenticationToken authenticationToken) throws AuthenticationFailedException  {
        return accessControlSession.getAccessSetForAuthToken(authenticationToken);
    }
    
    @Override
    public List<AccessSet> getUserAccessSets(final List<AuthenticationToken> authenticationTokens)  {
        final List<AccessSet> ret = new ArrayList<>();
        for (AuthenticationToken authToken : authenticationTokens) {
            // Always add, even if null. Otherwise the caller won't be able to determine which AccessSet belongs to which AuthenticationToken
            AccessSet as;
            try {
                as = accessControlSession.getAccessSetForAuthToken(authToken);
            } catch (AuthenticationFailedException e) {
                as = null;
            }
            ret.add(as);
        }
        return ret;
    }

    @Override
    public String testCall(AuthenticationToken authenticationToken, String argument1, int argument2) throws AuthorizationDeniedException, EjbcaException {
        // Simple example to prove that invocation of EJB works
        if (endEntityAccessSession!=null) {
            final EndEntityInformation eei = endEntityAccessSession.findUser("superadmin");
            if (eei!=null) {
                return eei.getDN();
            }
        }
        return "unknown (local call)";
    }

    @Override
    public String testCallPreferLocal(AuthenticationToken authenticationToken, String requestData) throws AuthorizationDeniedException {
        return "RaMasterApiLocalImpl.testCallPreferLocal";
    }

    @Override
    public List<String> testCallMerge(AuthenticationToken authenticationToken, String requestData) throws AuthorizationDeniedException {
        return Arrays.asList(new String[] {"RaMasterApiLocalImpl.testCallMerge"});
    }

    @Override
    public String testCallPreferCache(AuthenticationToken authenticationToken, String requestData) throws AuthorizationDeniedException {
        throw new UnsupportedOperationException();
    }
}
