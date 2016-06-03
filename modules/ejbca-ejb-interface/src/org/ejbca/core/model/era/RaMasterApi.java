/*************************************************************************
 *                                                                       *
 *  EJBCA Community: The OpenSource Certificate Authority                *
 *                                                                       *
 *  This software is free software; you can redistribute it and/or       *
 *  modify it under the terms of the GNU Lesser General Public           *
 *  License as published by the Free Software Foundation; either         *
 *  version 2.1 of the License, or any later version.                    *
 *                                                                       *
 *  See terms of license at gnu.org.                                     *
 *                                                                       *
 *************************************************************************/
package org.ejbca.core.model.era;

import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Map;

import org.cesecore.authentication.AuthenticationFailedException;
import org.cesecore.authentication.tokens.AuthenticationToken;
import org.cesecore.authorization.AuthorizationDeniedException;
import org.cesecore.authorization.access.AccessSet;
import org.cesecore.certificates.ca.CAInfo;
import org.cesecore.certificates.certificate.CertificateDataWrapper;
import org.cesecore.certificates.certificateprofile.CertificateProfile;
import org.cesecore.certificates.endentity.EndEntityInformation;
import org.ejbca.core.ejb.ra.EndEntityExistsException;
import org.ejbca.core.model.approval.AdminAlreadyApprovedRequestException;
import org.ejbca.core.model.approval.ApprovalException;
import org.ejbca.core.model.approval.ApprovalRequestExecutionException;
import org.ejbca.core.model.approval.ApprovalRequestExpiredException;
import org.ejbca.core.model.approval.SelfApprovalException;
import org.ejbca.core.model.approval.WaitingForApprovalException;
import org.ejbca.core.model.ra.raadmin.EndEntityProfile;

/**
 * API of available methods on the CA that can be invoked by the RA.
 * 
 * Keep in mind that there is latency, so batch things.
 * 
 * @version $Id$
 */
public interface RaMasterApi {

    /** @return true if the implementation if the interface is available and usable. */
    boolean isBackendAvailable();
    
    /** Returns an AccessSet containing the access rules that are allowed for the given authentication token. */
    AccessSet getUserAccessSet(AuthenticationToken authenticationToken) throws AuthenticationFailedException;
    
    /** Gets multiple access sets at once. Returns them in the same order as in the parameter */
    List<AccessSet> getUserAccessSets(List<AuthenticationToken> authenticationTokens);

    /** @return a list with information about non-external CAs that the caller is authorized to see. */
    List<CAInfo> getAuthorizedCas(AuthenticationToken authenticationToken);

    /** @return the approval request with the given id, or null if it doesn't exist or if authorization was denied */
    RaApprovalRequestInfo getApprovalRequest(AuthenticationToken authenticationToken, int id);

    /**
     * Finds an approval by a hash of the request data.
     * 
     * @param approvalId Calculated hash of the request (this somewhat confusing name is re-used from the ApprovalRequest class)
     */
    RaApprovalRequestInfo getApprovalRequestByRequestHash(AuthenticationToken authenticationToken, int approvalId);
    
    /**
     * Modifies an approval request and sets the current admin as a blacklisted admin.
     * @return The new approval request (which may have a new id)
     */
    RaApprovalRequestInfo editApprovalRequest(AuthenticationToken authenticationToken, RaApprovalEditRequest edit) throws AuthorizationDeniedException;
    
    /** Approves, rejects or saves (not yet implemented) a step of an approval request
     * @throws SelfApprovalException 
     * @throws AdminAlreadyApprovedRequestException 
     * @throws ApprovalRequestExecutionException 
     * @throws ApprovalRequestExpiredException 
     * @throws ApprovalException
     */
    boolean addRequestResponse(AuthenticationToken authenticationToken, RaApprovalResponseRequest requestResponse) throws AuthorizationDeniedException, ApprovalException, ApprovalRequestExpiredException, ApprovalRequestExecutionException, AdminAlreadyApprovedRequestException, SelfApprovalException;
    
    /** @return list of approval requests from the specified search criteria */
    RaRequestsSearchResponse searchForApprovalRequests(AuthenticationToken authenticationToken, RaRequestsSearchRequest raRequestsSearchRequest);
    
    /** @return CertificateDataWrapper if it exists and the caller is authorized to see the data or null otherwise*/
    CertificateDataWrapper searchForCertificate(AuthenticationToken authenticationToken, String fingerprint);
    
    /** @return list of certificates from the specified search criteria*/
    RaCertificateSearchResponse searchForCertificates(AuthenticationToken authenticationToken, RaCertificateSearchRequest raCertificateSearchRequest);

    /** @return list of end entities from the specified search criteria*/
    RaEndEntitySearchResponse searchForEndEntities(AuthenticationToken authenticationToken, RaEndEntitySearchRequest raEndEntitySearchRequest);

    /** @return map of authorized certificate profile Ids and each mapped name */
    Map<Integer, String> getAuthorizedCertificateProfileIdsToNameMap(AuthenticationToken authenticationToken);

    /** @return map of authorized entity profile Ids and each mapped name */
    Map<Integer, String> getAuthorizedEndEntityProfileIdsToNameMap(AuthenticationToken authenticationToken);

    /** @return map of authorized end entity profiles for the provided authentication token */
    IdNameHashMap<EndEntityProfile> getAuthorizedEndEntityProfiles(AuthenticationToken authenticationToken);

    /** @return map of authorized and enabled CAInfos for the provided authentication token*/
    IdNameHashMap<CAInfo> getAuthorizedCAInfos(AuthenticationToken authenticationToken);

    /** @return map of authorized certificate profiles for the provided authentication token*/
    IdNameHashMap<CertificateProfile> getAuthorizedCertificateProfiles(AuthenticationToken authenticationToken);
    
    /**
     * Adds (end entity) user.
     * @param admin authentication token
     * @param endEntity end entity data as EndEntityInformation object
     * @param clearpwd 
     * @throws AuthorizationDeniedException
     * @throws EndEntityExistsException if end entity already exists
     * @throws WaitingForApprovalException if approval is required to finalize the adding of the end entity
     * @return true if used has been added, false otherwise
     */
    boolean addUser(AuthenticationToken authenticationToken, EndEntityInformation endEntity, boolean clearpwd) throws AuthorizationDeniedException,
            EndEntityExistsException, WaitingForApprovalException;

    /**
     * Deletes (end entity) user. Does not propagate the exceptions but logs them.
     * @param authenticationToken
     * @param username the username of the end entity user about to delete
     * @throws AuthorizationDeniedException
     */
    void deleteUser(final AuthenticationToken authenticationToken, final String username) throws AuthorizationDeniedException;
    
    /**
     * Generates keystore for the specified end entity. Used for server side generated key pairs.
     * @param authenticationToken authentication token
     * @param endEntity holds end entity information (including user's password)
     * @return generated keystore
     * @throws AuthorizationDeniedException
     * @throws KeyStoreException if something went wrong with keystore creation
     */
    KeyStore generateKeystore(AuthenticationToken authenticationToken, EndEntityInformation endEntity) throws AuthorizationDeniedException, KeyStoreException;

    /**
     * Generates certificate from CSR for the specified end entity. Used for client side generated key pairs.
     * @param authenticationToken authentication token
     * @param endEntity end entity information
     * @param certificateRequest CSR as PKCS10CertificateRequst object
     * @return certificate binary data
     * @throws AuthorizationDeniedException
     */
    byte[] createCertificate(AuthenticationToken authenticationToken, EndEntityInformation endEntity,
            byte[] certificateRequest) throws AuthorizationDeniedException;

    /**
     * Signs the certificate and returns it as PKCS#7. CA about the sign is going to be found using issuer DN from the certificate.
     * @param authenticationToken authentication token
     * @param certificate certificate about to be signed
     * @param includeChain true if all chain should be included, false otherwise
     * @return PKCS#7 binary data
     * @throws AuthorizationDeniedException
     */
    byte[] createPkcs7(AuthenticationToken authenticationToken, X509Certificate certificate, boolean includeChain)
            throws AuthorizationDeniedException;

    /**
     * Finds end entity by its username.
     * @param authenticationToken authentication token
     * @param username username of the end entity
     * @return end entity as EndEntityInformation
     * @throws AuthorizationDeniedException
     */
    EndEntityInformation searchUser(AuthenticationToken authenticationToken, String username);

    /**
     * Request status change of a certificate (revoke or reactivate).
     * Requires authorization to CA, EEP for the certificate and '/ra_functionality/revoke_end_entity'.
     * 
     * @param authenticationToken of the requesting administrator or client
     * @param fingerprint of the certificate
     * @param newStatus CertificateConstants.CERT_REVOKED (40) or CertificateConstants.CERT_ACTIVE (20)
     * @param newRevocationReason One of RevokedCertInfo.REVOCATION_REASON_...
     * @return true if the operation was successful, false if the certificate could not be revoked for example since it did not exist
     * @throws ApprovalException if there was a problem creating the approval request
     * @throws WaitingForApprovalException if the request has been sent for approval
     */
    boolean changeCertificateStatus(AuthenticationToken authenticationToken, String fingerprint, int newStatus, int newRevocationReason)
            throws ApprovalException, WaitingForApprovalException;

    /**
     * Finds the certificate profile by certificate profile id
     * 
     * @param authenticationToken of the requesting administrator or client
     * @param certificateProfileId certificate profile unique id
     * @return certificate profile as CertificateProfile object or null if it can not be found
     */
    CertificateProfile searchCertificateProfile(AuthenticationToken authenticationToken, int certificateProfileId);

}