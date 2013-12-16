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

package org.ejbca.core.protocol.cmp.authentication;

import java.security.cert.Certificate;

import org.cesecore.authentication.tokens.AuthenticationToken;
import org.cesecore.certificates.ca.CaSession;
import org.ejbca.config.CmpConfiguration;

/**
 * Check the authentication of the PKIMessage by verifying the signature from a Vendor CA (3GPP mode)
 * 
 * @version $Id$
 *
 */
public interface CmpVendorMode {


    void setCaSession(final CaSession caSession);

    void setCmpConfiguration(final CmpConfiguration cmpConfiguration);
    
    /** Checks if the certificate is issued by a configured Vendor CA, and that it can be verified using that Vendor CA certificate
     * @param admin administrator making the call, must have access to get CAInfo for the Vendor CA
     * @param confAlias the CMP alias in use
     * @param extraCert certificate from extraCert field of CMP request 
     * @return true if issued, and verified by a Vendor CA in the specified CMP alias, false otherwise.
     */
    boolean isExtraCertIssuedByVendorCA(final AuthenticationToken admin, final String confAlias, final Certificate extraCert);
    
    /**
     * Checks whether authentication by vendor-issued-certificate should be used. It can be used only in client mode and with initialization/certification requests.
     *  
     * @param reqType
     * @return 'True' if authentication by vendor-issued-certificate is used. 'False' otherwise
     */
    boolean isVendorCertificateMode(final int reqType, final String confAlias);
}
