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

package org.ejbca.core.protocol.cmp;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import org.apache.log4j.Logger;
import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.x509.X509Extensions;
import org.bouncycastle.asn1.x509.X509Name;
import org.cesecore.certificates.certificate.request.CertificateResponseMessage;
import org.cesecore.certificates.certificate.request.RequestMessage;
import org.cesecore.util.Base64;
import org.cesecore.util.CertTools;
import org.ejbca.config.CmpConfiguration;

import com.novosec.pkix.asn1.cmp.PKIHeader;
import com.novosec.pkix.asn1.cmp.PKIMessage;

/**
 * Nested Message Content according to RFC4210. The PKI message is signed by an RA authority.
 * The PKIMessage body is another PKIMessage containing the request to be processed. 
 * 
 * @version $Id: NestedMessageContent.java 12744 2011-09-28 08:48:48Z aveen4711 $
 *
 */
public class NestedMessageContent extends BaseCmpMessage implements RequestMessage {

    private static final long serialVersionUID = 1L;
    
    private static final Logger log = Logger.getLogger(NestedMessageContent.class);
    
    private PKIMessage raSignedMessage;
    
    /** Because PKIMessage is not serializable we need to have the serializable bytes save as well, so 
     * we can restore the PKIMessage after serialization/deserialization. */ 
    private byte[] pkimsgbytes = null;

    public NestedMessageContent() {}
    
    public NestedMessageContent(final PKIMessage pkiMsg) {
        this.raSignedMessage = pkiMsg;
        setPKIMessageBytes(pkiMsg);
        //this.originalMessage = pkiMsg.getBody().getNested();
        init();
    }
    
    private void init() {
        final PKIHeader header = getPKIMessage().getHeader();
        DEROctetString os = header.getTransactionID();
        if (os != null) {
            final byte[] val = os.getOctets();
            if (val != null) {
                setTransactionId(new String(Base64.encode(val)));                           
            }
        }

        os = header.getSenderNonce();
        if (os != null) {
            final byte[] val = os.getOctets();
            if (val != null) {
                setSenderNonce(new String(Base64.encode(val)));                         
            }
        }
        setRecipient(header.getRecipient());
        setSender(header.getSender());
    }

    public PKIMessage getPKIMessage() {
        if (getMessage() == null) {
            try {
                setMessage(PKIMessage.getInstance(new ASN1InputStream(new ByteArrayInputStream(pkimsgbytes)).readObject()));                
            } catch (IOException e) {
                log.error("Error decoding bytes for PKIMessage: ", e);
            }
        }
        return getMessage();
    }
    public void setPKIMessageBytes(final PKIMessage msg) {
        try {
            this.pkimsgbytes = msg.getDERObject().getEncoded();
        } catch (IOException e) {
            log.error("Error getting encoded bytes from PKIMessage: ", e);
        }
        setMessage(msg);
    }

    
    @Override
    /**
     * Verifies the signature of the pkimessage using the trusted RA certificate stored in cmpConfiguration.getRaCertificatePath()
     * 
     * @return True if the verification succeeds. False otherwise.
     */
    public boolean verify() {
        boolean ret = false;
        try {
            final List<X509Certificate> racerts = getRaCerts();
            if(log.isDebugEnabled()) {
                log.debug("Found " + racerts.size() + " certificates in " + CmpConfiguration.getRaCertificatePath());
            }
            if(racerts.size() <= 0) {
                String errorMessage = "No certificate files were found in " + CmpConfiguration.getRaCertificatePath();
                log.error(errorMessage);
            }

            final Iterator<X509Certificate> itr = racerts.iterator();
            X509Certificate cert = null;
            while(itr.hasNext() && !ret) {
                cert = itr.next();
                if(log.isDebugEnabled()) {
                    log.debug("Trying to verifying the NestedMessageContent using the RA certificate with subjectDN '" + cert.getSubjectDN() + "'");
                }

                try {
                    cert.checkValidity();
                } catch(Exception e) {
                    if(log.isDebugEnabled()) {
                        log.debug("Certificate with subjectDN '" + CertTools.getSubjectDN(cert) + "' is no longer valid.");
                        log.debug(e.getLocalizedMessage());
                    }
                    continue;
                }
                
                Signature sig = Signature.getInstance(cert.getSigAlgName(), "BC");
                sig.initVerify(cert.getPublicKey());
                sig.update(raSignedMessage.getProtectedBytes());
                if(raSignedMessage.getProtection() != null) {
                    ret = sig.verify(raSignedMessage.getProtection().getBytes());
                    if(log.isDebugEnabled()) {
                        log.debug("Verifying the NestedMessageContent using the RA certificate with subjectDN '" + cert.getSubjectDN() + "' returned " + ret);
                    }
                } else {
                    log.error("No signature was found in NestedMessageContent");
                }
            }

        } catch (CertificateException e) {
            if(log.isDebugEnabled()) {
                log.debug(e.getLocalizedMessage());
            }
        } catch (IOException e) {
            if(log.isDebugEnabled()) {
                log.debug(e.getLocalizedMessage());
            }
        } catch (NoSuchAlgorithmException e) {
            if(log.isDebugEnabled()) {
                log.debug(e.getLocalizedMessage());
            }
        } catch (NoSuchProviderException e) {
            if(log.isDebugEnabled()) {
                log.debug(e.getLocalizedMessage());
            }
        } catch (InvalidKeyException e) {
            if(log.isDebugEnabled()) {
                log.debug(e.getLocalizedMessage());
            }
        } catch (SignatureException e) {
            if(log.isDebugEnabled()) {
                log.debug(e.getLocalizedMessage());
            }
        }

        if(log.isDebugEnabled()) {
            log.debug("Verifying the NestedMessageContent returned " + ret);
        }

        return ret;
    }

    /**
     * Reads the files in cmpConfiguration.getRaCertificatePath() and returns them as a list of certificates.
     *  
     * The certificate files should be PEM encoded.
     * 
     * @return A list of the certificates in cmpConfiguration.getRaCertificatePath(). 
     * @throws CertificateException
     * @throws IOException
     */
    private List<X509Certificate> getRaCerts() throws CertificateException, IOException {
            
        final List<X509Certificate> racerts = new ArrayList<X509Certificate>();
        final String raCertsPath = CmpConfiguration.getRaCertificatePath();
        if(log.isDebugEnabled()) {
            log.debug("Looking for trusted RA certificate in " + raCertsPath);
        }

        final File raCertDirectory = new File(raCertsPath);
        final String[] files = raCertDirectory.list();
        if(log.isDebugEnabled()) {
            log.debug("Found " + files.length + " trusted RA certificate in " + raCertsPath);
        }

        String filepath;
        if(files != null) {
            for(String certFile : files) {
                filepath = raCertsPath + "/" + certFile;
                if(log.isDebugEnabled()) {
                    log.debug("Reading certificate from " + filepath);
                }

                racerts.add((X509Certificate) CertTools.getCertsFromPEM(filepath).iterator().next());
                if(log.isDebugEnabled()) {
                    log.debug("Added " + certFile + " to the list of trusted RA certificates");
                }

            }
        }       
        return racerts;
    }
    
    
    



    @Override
    public String getCRLIssuerDN() {
        return null;
    }

    @Override
    public BigInteger getCRLSerialNo() {
        return null;
    }

    @Override
    public int getErrorNo() {
        return 0;
    }

    @Override
    public String getErrorText() {
        return null;
    }

    @Override
    public String getIssuerDN() {
        return null;
    }

    @Override
    public String getPassword() {
        return null;
    }

    @Override
    public String getPreferredDigestAlg() {
        return null;
    }

    @Override
    public String getRequestAltNames() {
        return null;
    }

    @Override
    public String getRequestDN() {
        return null;
    }

    @Override
    public X509Extensions getRequestExtensions() {
        return null;
    }

    @Override
    public int getRequestId() {
        return 0;
    }

    @Override
    public byte[] getRequestKeyInfo() {
        return null;
    }

    @Override
    public PublicKey getRequestPublicKey() throws InvalidKeyException,
            NoSuchAlgorithmException, NoSuchProviderException {
        return null;
    }

    @Override
    public int getRequestType() {
        return 0;
    }

    @Override
    public Date getRequestValidityNotAfter() {
        return null;
    }

    @Override
    public Date getRequestValidityNotBefore() {
        return null;
    }

    @Override
    public X509Name getRequestX509Name() {
        return null;
    }

    @Override
    public BigInteger getSerialNo() {
        return null;
    }

    @Override
    public String getUsername() {
        return null;
    }

    @Override
    public boolean includeCACert() {
        return false;
    }

    @Override
    public boolean requireKeyInfo() {
        return false;
    }

    @Override
    public void setKeyInfo(final Certificate cert, final PrivateKey key, final String provider) {}

    @Override
    public CertificateResponseMessage createResponseMessage(Class responseClass, RequestMessage req, Certificate cert, PrivateKey signPriv,
            String provider) {
        return null;
    }
}
