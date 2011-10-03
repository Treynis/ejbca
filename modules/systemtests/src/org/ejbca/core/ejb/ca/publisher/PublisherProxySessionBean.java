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
package org.ejbca.core.ejb.ca.publisher;

import java.security.cert.Certificate;
import java.util.Collection;

import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;

import org.cesecore.authentication.tokens.AuthenticationToken;
import org.cesecore.authorization.AuthorizationDeniedException;
import org.cesecore.jndi.JndiConstants;
import org.ejbca.core.model.ca.publisher.BasePublisher;
import org.ejbca.core.model.ca.publisher.PublisherConnectionException;
import org.ejbca.core.model.ca.publisher.PublisherExistsException;

/**
 * @version $Id$
 *
 */

@Stateless(mappedName = JndiConstants.APP_JNDI_PREFIX + "PublisherProxySessionRemote")
@TransactionAttribute(TransactionAttributeType.REQUIRED)
public class PublisherProxySessionBean implements PublisherProxySessionRemote {

    @EJB
    private PublisherSessionLocal publisherSession;
    
    @Override
    public void addPublisher(AuthenticationToken admin, String name, BasePublisher publisher) throws PublisherExistsException {
        publisherSession.addPublisher(admin, name, publisher);

    }

    @Override
    public void changePublisher(AuthenticationToken admin, String name, BasePublisher publisher) {
        publisherSession.changePublisher(admin, name, publisher);

    }

    @Override
    public void clonePublisher(AuthenticationToken admin, String oldname, String newname) {
        publisherSession.clonePublisher(admin, oldname, newname);
    }

    @Override
    public int getPublisherId(AuthenticationToken admin, String name) {
        return publisherSession.getPublisherId(admin, name);
    }

    @Override
    public void removePublisher(AuthenticationToken admin, String name) {
       publisherSession.removePublisher(admin, name);

    }

    @Override
    public void renamePublisher(AuthenticationToken admin, String oldname, String newname) throws PublisherExistsException {
        publisherSession.renamePublisher(admin, oldname, newname);

    }

    @Override
    public void revokeCertificate(AuthenticationToken admin, Collection<Integer> publisherids, Certificate cert, String username, String userDN,
            String cafp, int type, int reason, long revocationDate, String tag, int certificateProfileId, long lastUpdate) throws AuthorizationDeniedException {
        publisherSession.revokeCertificate(admin, publisherids, cert, username, userDN, cafp, type, reason, revocationDate, tag, certificateProfileId, lastUpdate);

    }

    @Override
    public void testConnection(AuthenticationToken admin, int publisherid) throws PublisherConnectionException {
        publisherSession.testConnection(admin, publisherid);
    }

}
