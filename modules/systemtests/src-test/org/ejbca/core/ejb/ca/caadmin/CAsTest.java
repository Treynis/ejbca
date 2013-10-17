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

package org.ejbca.core.ejb.ca.caadmin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import java.lang.reflect.Field;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.Principal;
import java.security.PublicKey;
import java.security.cert.CertPathValidatorException;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.security.interfaces.DSAPublicKey;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import javax.security.auth.x500.X500Principal;

import org.apache.log4j.Logger;
import org.bouncycastle.jcajce.provider.asymmetric.dstu.BCDSTU4145PublicKey;
import org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPublicKey;
import org.bouncycastle.jcajce.provider.asymmetric.ecgost.BCECGOST3410PublicKey;
import org.bouncycastle.jce.provider.JCEECPublicKey;
import org.bouncycastle.jce.spec.ECParameterSpec;
import org.cesecore.authentication.tokens.AuthenticationSubject;
import org.cesecore.authentication.tokens.AuthenticationToken;
import org.cesecore.authentication.tokens.UsernamePrincipal;
import org.cesecore.authorization.AuthorizationDeniedException;
import org.cesecore.certificates.ca.CAConstants;
import org.cesecore.certificates.ca.CADoesntExistsException;
import org.cesecore.certificates.ca.CAExistsException;
import org.cesecore.certificates.ca.CAInfo;
import org.cesecore.certificates.ca.CVCCAInfo;
import org.cesecore.certificates.ca.CaSessionRemote;
import org.cesecore.certificates.ca.X509CAInfo;
import org.cesecore.certificates.ca.catoken.CAToken;
import org.cesecore.certificates.ca.catoken.CATokenConstants;
import org.cesecore.certificates.ca.extendedservices.ExtendedCAServiceInfo;
import org.cesecore.certificates.certificate.CertificateConstants;
import org.cesecore.certificates.certificate.CertificateCreateSessionRemote;
import org.cesecore.certificates.certificate.CertificateInfo;
import org.cesecore.certificates.certificate.CertificateStoreSessionRemote;
import org.cesecore.certificates.certificate.InternalCertificateStoreSessionRemote;
import org.cesecore.certificates.certificate.request.CVCRequestMessage;
import org.cesecore.certificates.certificate.request.CertificateResponseMessage;
import org.cesecore.certificates.certificate.request.PKCS10RequestMessage;
import org.cesecore.certificates.certificate.request.RequestMessageUtils;
import org.cesecore.certificates.certificate.request.ResponseMessage;
import org.cesecore.certificates.certificate.request.X509ResponseMessage;
import org.cesecore.certificates.certificateprofile.CertificatePolicy;
import org.cesecore.certificates.certificateprofile.CertificateProfile;
import org.cesecore.certificates.certificateprofile.CertificateProfileConstants;
import org.cesecore.certificates.certificateprofile.CertificateProfileSessionRemote;
import org.cesecore.certificates.crl.RevokedCertInfo;
import org.cesecore.certificates.endentity.EndEntityInformation;
import org.cesecore.certificates.endentity.EndEntityTypes;
import org.cesecore.certificates.util.AlgorithmConstants;
import org.cesecore.certificates.util.AlgorithmTools;
import org.cesecore.keys.token.CryptoToken;
import org.cesecore.keys.token.CryptoTokenAuthenticationFailedException;
import org.cesecore.keys.token.CryptoTokenManagementProxySessionRemote;
import org.cesecore.keys.token.CryptoTokenManagementSessionRemote;
import org.cesecore.keys.token.CryptoTokenManagementSessionTest;
import org.cesecore.keys.token.CryptoTokenNameInUseException;
import org.cesecore.keys.token.CryptoTokenOfflineException;
import org.cesecore.keys.token.SoftCryptoToken;
import org.cesecore.keys.token.p11.exception.NoSuchSlotException;
import org.cesecore.keys.util.KeyTools;
import org.cesecore.mock.authentication.SimpleAuthenticationProviderSessionRemote;
import org.cesecore.mock.authentication.tokens.TestAlwaysAllowLocalAuthenticationToken;
import org.cesecore.util.Base64;
import org.cesecore.util.CertTools;
import org.cesecore.util.CryptoProviderTools;
import org.cesecore.util.EjbRemoteHelper;
import org.cesecore.util.SimpleTime;
import org.cesecore.util.StringTools;
import org.ejbca.core.EjbcaException;
import org.ejbca.core.ejb.ca.CaTestCase;
import org.ejbca.core.ejb.ca.publisher.PublisherProxySessionRemote;
import org.ejbca.core.model.SecConst;
import org.ejbca.core.model.ca.caadmin.extendedcaservices.CmsCAServiceInfo;
import org.ejbca.core.model.ca.caadmin.extendedcaservices.HardTokenEncryptCAServiceInfo;
import org.ejbca.core.model.ca.caadmin.extendedcaservices.KeyRecoveryCAServiceInfo;
import org.ejbca.core.model.ca.caadmin.extendedcaservices.XKMSCAServiceInfo;
import org.ejbca.core.model.ca.publisher.ValidationAuthorityPublisher;
import org.ejbca.core.protocol.cmp.CmpResponseMessage;
import org.ejbca.cvc.CVCAuthenticatedRequest;
import org.ejbca.cvc.CVCObject;
import org.ejbca.cvc.CVCertificate;
import org.ejbca.cvc.CardVerifiableCertificate;
import org.ejbca.cvc.CertificateParser;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Tests CA administration.
 * 
 * @version $Id$
 */
public class CAsTest extends CaTestCase {

    private static final Logger log = Logger.getLogger(CAsTest.class);
    private static final AuthenticationToken admin = new TestAlwaysAllowLocalAuthenticationToken(new UsernamePrincipal("CAsTest"));

    private final CAAdminSessionRemote caAdminSession = EjbRemoteHelper.INSTANCE.getRemoteSession(CAAdminSessionRemote.class);
    private final CaSessionRemote caSession = EjbRemoteHelper.INSTANCE.getRemoteSession(CaSessionRemote.class);
    private final PublisherProxySessionRemote publisherProxySession = EjbRemoteHelper.INSTANCE.getRemoteSession(PublisherProxySessionRemote.class, EjbRemoteHelper.MODULE_TEST);
    private final CertificateStoreSessionRemote certificateStoreSession = EjbRemoteHelper.INSTANCE.getRemoteSession(CertificateStoreSessionRemote.class);
    private final InternalCertificateStoreSessionRemote internalCertStoreSession = EjbRemoteHelper.INSTANCE.getRemoteSession(InternalCertificateStoreSessionRemote.class, EjbRemoteHelper.MODULE_TEST);
    private final CertificateProfileSessionRemote certificateProfileSession = EjbRemoteHelper.INSTANCE.getRemoteSession(CertificateProfileSessionRemote.class);
    private final CryptoTokenManagementProxySessionRemote cryptoTokenManagementProxySession = EjbRemoteHelper.INSTANCE.getRemoteSession(CryptoTokenManagementProxySessionRemote.class, EjbRemoteHelper.MODULE_TEST);
    private final SimpleAuthenticationProviderSessionRemote simpleAuthenticationProvider = EjbRemoteHelper.INSTANCE.getRemoteSession(SimpleAuthenticationProviderSessionRemote.class, EjbRemoteHelper.MODULE_TEST);
    private final CryptoTokenManagementSessionRemote cryptoTokenManagementSession = EjbRemoteHelper.INSTANCE.getRemoteSession(CryptoTokenManagementSessionRemote.class);

    // private AuthenticationToken adminTokenNoAuth;

    @BeforeClass
    public static void beforeClass() throws Exception {
        CryptoProviderTools.installBCProvider();
        createTestCA();
    }
    
    @AfterClass
    public static void afterClass() throws Exception {
        removeTestCA();
    }

    @Before
    public void setUp() throws Exception {
        addDefaultRole();
    }

    @After
    public void tearDown() throws Exception {
        removeDefaultRole();
    }

    public String getRoleName() {
        return "CAsTest"; 
    }

    protected CAToken createCaToken(String tokenName, String signKeySpec, String sigAlg, String encAlg) {
        final Properties cryptoTokenProperties = new Properties();
        cryptoTokenProperties.setProperty(CryptoToken.AUTOACTIVATE_PIN_PROPERTY, "foo1234");
        final int cryptoTokenId;
        try {
            cryptoTokenId = cryptoTokenManagementSession.createCryptoToken(admin, this.getClass().getSimpleName() + "." + tokenName, SoftCryptoToken.class.getName(), cryptoTokenProperties, null, null);
            cryptoTokenManagementSession.createKeyPair(admin, cryptoTokenId, CAToken.SOFTPRIVATESIGNKEYALIAS, signKeySpec);
            cryptoTokenManagementSession.createKeyPair(admin, cryptoTokenId, CAToken.SOFTPRIVATEDECKEYALIAS, "1024");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        // Create CAToken (what key in the CryptoToken should be used for what)
        final Properties caTokenProperties = new Properties();
        caTokenProperties.setProperty(CATokenConstants.CAKEYPURPOSE_CERTSIGN_STRING, CAToken.SOFTPRIVATESIGNKEYALIAS);
        caTokenProperties.setProperty(CATokenConstants.CAKEYPURPOSE_CRLSIGN_STRING, CAToken.SOFTPRIVATESIGNKEYALIAS);
        caTokenProperties.setProperty(CATokenConstants.CAKEYPURPOSE_DEFAULT_STRING, CAToken.SOFTPRIVATEDECKEYALIAS);
        final CAToken catoken = new CAToken(cryptoTokenId, caTokenProperties);
        catoken.setSignatureAlgorithm(sigAlg);
        catoken.setEncryptionAlgorithm(encAlg);
        catoken.setKeySequence(CAToken.DEFAULT_KEYSEQUENCE);
        catoken.setKeySequenceFormat(StringTools.KEY_SEQUENCE_FORMAT_NUMERIC);
        return catoken;
    }

    /** Adds a CA using RSA keys to the database. It also checks that the CA is stored correctly. */
    @Test
    public void test01AddRSACA() throws Exception {
        log.trace(">" + Thread.currentThread().getStackTrace()[1].getMethodName() + "()");
        final String caName = getTestCAName();
        // Preemptively remove the CA if it exists.
        removeTestCA(caName);
        final CAToken catoken = createCaToken("test01", "1024", AlgorithmConstants.SIGALG_SHA1_WITH_RSA, AlgorithmConstants.SIGALG_SHA1_WITH_RSA);
        // Create and active OSCP CA Service.
        final List<ExtendedCAServiceInfo> extendedcaservices = new ArrayList<ExtendedCAServiceInfo>();
        extendedcaservices.add(new XKMSCAServiceInfo(ExtendedCAServiceInfo.STATUS_INACTIVE, "CN=XKMSCertificate, " + "CN=TEST", "", "1024",
                AlgorithmConstants.KEYALGORITHM_RSA));
        extendedcaservices.add(new HardTokenEncryptCAServiceInfo(ExtendedCAServiceInfo.STATUS_ACTIVE));
        extendedcaservices.add(new KeyRecoveryCAServiceInfo(ExtendedCAServiceInfo.STATUS_ACTIVE));

        List<CertificatePolicy> policies = new ArrayList<CertificatePolicy>();
        CertificatePolicy pol = new CertificatePolicy("1.2.3.4", null, null);
        policies.add(pol);
        X509CAInfo cainfo = new X509CAInfo("CN=TEST", caName, CAConstants.CA_ACTIVE, new Date(), "", CertificateProfileConstants.CERTPROFILE_FIXED_ROOTCA, 3650, null, // Expiretime
                CAInfo.CATYPE_X509, CAInfo.SELFSIGNED, (Collection<Certificate>) null, catoken, "JUnit RSA CA", -1, null, 
                policies, // PolicyId
                24 * SimpleTime.MILLISECONDS_PER_HOUR, // CRLPeriod
                0 * SimpleTime.MILLISECONDS_PER_HOUR, // CRLIssueInterval
                10 * SimpleTime.MILLISECONDS_PER_HOUR, // CRLOverlapTime
                10 * SimpleTime.MILLISECONDS_PER_HOUR, // DeltaCRLPeriod
                new ArrayList<Integer>(), true, // Authority Key Identifier
                false, // Authority Key Identifier Critical
                true, // CRL Number
                false, // CRL Number Critical
                null, // defaultcrldistpoint
                null, // defaultcrlissuer
                null, // defaultocsplocator
                null, // Authority Information Access
                null, // defaultfreshestcrl
                true, // Finish User
                extendedcaservices, false, // use default utf8 settings
                new ArrayList<Integer>(), // Approvals Settings
                1, // Number of Req approvals
                false, // Use UTF8 subject DN by default
                true, // Use LDAP DN order by default
                false, // Use CRL Distribution Point on CRL
                false, // CRL Distribution Point on CRL critical
                true, true, // isDoEnforceUniquePublicKeys
                true, // isDoEnforceUniqueDistinguishedName
                false, // isDoEnforceUniqueSubjectDNSerialnumber
                false, // useCertReqHistory
                true, // useUserStorage
                true, // useCertificateStorage
                null // cmpRaAuthSecret
        );
        caAdminSession.createCA(admin, cainfo);
        CAInfo info = caSession.getCAInfo(caAdmin, caName);
        Collection<Certificate> rootcacertchain = info.getCertificateChain();
        X509Certificate cert = (X509Certificate) rootcacertchain.iterator().next();
        String sigAlg = AlgorithmTools.getSignatureAlgorithm(cert);
        assertEquals(AlgorithmConstants.SIGALG_SHA1_WITH_RSA, sigAlg);
        assertTrue("Error in created ca certificate", cert.getSubjectDN().toString().equals("CN=TEST"));
        assertTrue("Creating CA failed", info.getSubjectDN().equals("CN=TEST"));
        assertEquals("Public key is not RSA", AlgorithmConstants.KEYALGORITHM_RSA, AlgorithmTools.getKeyAlgorithm(cert.getPublicKey()));
        assertTrue(
                "CA is not valid for the specified duration.",
                cert.getNotAfter().after(new Date(new Date().getTime() + 10 * 364 * 24 * 60 * 60 * 1000L))
                        && cert.getNotAfter().before(new Date(new Date().getTime() + 10 * 366 * 24 * 60 * 60 * 1000L)));
        String policyId = CertTools.getCertificatePolicyId(cert, 0);
        assertNotNull("CA certificate should have a Certificate Policy from CA settings", policyId);
        assertEquals("1.2.3.4", policyId);

        // Test to generate a certificate request from the CA
        Collection<Certificate> cachain = info.getCertificateChain();
        // Check CMP RA secret, default value empty string
        X509CAInfo xinfo = (X509CAInfo) info;
        assertNotNull(xinfo.getCmpRaAuthSecret());
        assertEquals("", xinfo.getCmpRaAuthSecret());
        byte[] request = caAdminSession.makeRequest(caAdmin, info.getCAId(), cachain, catoken.getAliasFromPurpose(CATokenConstants.CAKEYPURPOSE_CERTSIGN));
        PKCS10RequestMessage msg = new PKCS10RequestMessage(request);
        assertEquals("CN=TEST", msg.getRequestDN());
    }

    /** Renames CA in database. */
    @Test
    public void test02RenameCA() throws Exception {
        log.trace(">test02RenameCA()");
        boolean ret = false;
        try {
            caSession.renameCA(admin, getTestCAName(), "TEST2");
            caSession.renameCA(admin, "TEST2", getTestCAName());
            ret = true;
        } catch (CAExistsException cee) {
        }
        assertTrue("Renaming CA failed", ret);
        log.trace("<test02RenameCA()");
    }

    /** Edits ca and checks that it's stored correctly. */
    @Test
    public void testEditCA() throws Exception {
        log.trace(">test03EditCA()");
        X509CAInfo info = (X509CAInfo) caSession.getCAInfo(admin, getTestCAName());
        info.setCRLPeriod(33);
        caAdminSession.editCA(admin, info);
        X509CAInfo info2 = (X509CAInfo) caSession.getCAInfo(admin, getTestCAName());
        assertTrue("Editing CA failed", info2.getCRLPeriod() == 33);
        log.trace("<test03EditCA()");
    }

    /**
     * Tests creating an unitialized CA and then initializing it.
     */
    @Test
    public void testInitializeCa() throws Exception {
        final String caName = "testInitializeCa";
        
        CAInfo x509CaInfo = createUnititializedCaInfo(caName, caName);
        caAdminSession.createCA(admin, x509CaInfo);
        try {
            CAInfo retrievedCaInfo = caSession.getCAInfo(admin, x509CaInfo.getCAId());
            assertEquals("CA was not created unitialized", CAConstants.CA_UNINITIALIZED, retrievedCaInfo.getStatus());
            assertTrue("Unitialized CA was given certificate chain", retrievedCaInfo.getCertificateChain().isEmpty());
            //Now initialize
            caAdminSession.initializeCa(admin, retrievedCaInfo);
            CAInfo updatedCaInfo = caSession.getCAInfo(admin, retrievedCaInfo.getCAId());
            assertEquals("CA was not set to active", CAConstants.CA_ACTIVE, updatedCaInfo.getStatus());
            assertFalse("Initialized CA was not given certificate chain", updatedCaInfo.getCertificateChain().isEmpty());
        } finally {
            removeOldCa(caName);
        }
    }
    
    /**
     * Tests creating an unitialized CA and then initializing it.
     */
    @Test
    public void testInitializeCaAndChangeSubjectDn() throws Exception {
        final String caName = "testInitializeCaAndChangeSubjectDn";
        
        CAInfo x509CaInfo = createUnititializedCaInfo(caName, caName);
        caAdminSession.createCA(admin, x509CaInfo);
        try {
            CAInfo retrievedCaInfo = caSession.getCAInfo(admin, x509CaInfo.getCAId());
            assertEquals("CA was not created unitialized", CAConstants.CA_UNINITIALIZED, retrievedCaInfo.getStatus());
            assertTrue("Unitialized CA was given certificate chain", retrievedCaInfo.getCertificateChain().isEmpty());
            //Now change a value and initialize
            final String alternatCaDn = "CN=foo";
            retrievedCaInfo.setSubjectDN(alternatCaDn);
            caAdminSession.initializeCa(admin, retrievedCaInfo);
            CAInfo updatedCaInfo = caSession.getCAInfo(admin, CertTools.stringToBCDNString(alternatCaDn).hashCode());
            assertEquals("CA was not set to active", CAConstants.CA_ACTIVE, updatedCaInfo.getStatus());
            assertFalse("Initialized CA was not given certificate chain", updatedCaInfo.getCertificateChain().isEmpty());
        } finally {
            removeOldCa(caName);
        }
    }
    
    private CAInfo createUnititializedCaInfo(String cryptoTokenName, String caName) throws CryptoTokenOfflineException, CryptoTokenAuthenticationFailedException, CryptoTokenNameInUseException, AuthorizationDeniedException, InvalidKeyException, InvalidAlgorithmParameterException {
        final Properties cryptoTokenProperties = new Properties();
        cryptoTokenProperties.setProperty(CryptoToken.AUTOACTIVATE_PIN_PROPERTY, "foo123");
        int cryptoTokenId;
        if (!cryptoTokenManagementProxySession.isCryptoTokenNameUsed(cryptoTokenName)) {
            try {
                cryptoTokenId = cryptoTokenManagementSession.createCryptoToken(admin, cryptoTokenName, SoftCryptoToken.class.getName(),
                        cryptoTokenProperties, null, null);
            } catch (NoSuchSlotException e) {
                throw new RuntimeException("Attempted to find a slot for a soft crypto token. This should not happen.");
            }
        } else {
            cryptoTokenId = cryptoTokenManagementSession.getIdFromName(cryptoTokenName);
        }
        if (!cryptoTokenManagementSession.isAliasUsedInCryptoToken(cryptoTokenId, CAToken.SOFTPRIVATESIGNKEYALIAS)) {
            cryptoTokenManagementSession.createKeyPair(admin, cryptoTokenId, CAToken.SOFTPRIVATESIGNKEYALIAS, "1024");
        }
        if (!cryptoTokenManagementSession.isAliasUsedInCryptoToken(cryptoTokenId, CAToken.SOFTPRIVATEDECKEYALIAS)) {
            cryptoTokenManagementSession.createKeyPair(admin, cryptoTokenId, CAToken.SOFTPRIVATEDECKEYALIAS, "1024");
        }

        final CryptoToken cryptoToken = cryptoTokenManagementProxySession.getCryptoToken(cryptoTokenId);
        
        Properties caTokenProperties = new Properties();
        caTokenProperties.setProperty(CATokenConstants.CAKEYPURPOSE_CERTSIGN_STRING, CAToken.SOFTPRIVATESIGNKEYALIAS);
        caTokenProperties.setProperty(CATokenConstants.CAKEYPURPOSE_CRLSIGN_STRING, CAToken.SOFTPRIVATESIGNKEYALIAS);
        caTokenProperties.setProperty(CATokenConstants.CAKEYPURPOSE_DEFAULT_STRING, CAToken.SOFTPRIVATEDECKEYALIAS);
        CAToken catoken = new CAToken(cryptoToken.getId(), caTokenProperties);
        // Set key sequence so that next sequence will be 00001 (this is the default though so not really needed here)
        catoken.setKeySequence(CAToken.DEFAULT_KEYSEQUENCE);
        catoken.setKeySequenceFormat(StringTools.KEY_SEQUENCE_FORMAT_NUMERIC);
        catoken.setSignatureAlgorithm(AlgorithmConstants.SIGALG_SHA256_WITH_RSA);
        catoken.setEncryptionAlgorithm(AlgorithmConstants.SIGALG_SHA256_WITH_RSA);
        X509CAInfo x509CaInfo = new X509CAInfo("CN="+caName, caName, CAConstants.CA_UNINITIALIZED, new Date(), "", CertificateProfileConstants.CERTPROFILE_FIXED_ROOTCA,
                3650, null, // Expiretime
                CAInfo.CATYPE_X509, CAInfo.SELFSIGNED, (Collection<Certificate>) null, catoken, "JUnit RSA CA", -1, null, null, // PolicyId
                24 * SimpleTime.MILLISECONDS_PER_HOUR, // CRLPeriod
                0 * SimpleTime.MILLISECONDS_PER_HOUR, // CRLIssueInterval
                10 * SimpleTime.MILLISECONDS_PER_HOUR, // CRLOverlapTime
                10 * SimpleTime.MILLISECONDS_PER_HOUR, // DeltaCRLPeriod
                new ArrayList<Integer>(), true, // Authority Key Identifier
                false, // Authority Key Identifier Critical
                true, // CRL Number
                false, // CRL Number Critical
                null, // defaultcrldistpoint
                null, // defaultcrlissuer
                null, // defaultocsplocator
                null, // Authority Information Access
                null, // defaultfreshestcrl
                true, // Finish User
                new ArrayList<ExtendedCAServiceInfo>(), false, // use default utf8 settings
                new ArrayList<Integer>(), // Approvals Settings
                1, // Number of Req approvals
                false, // Use UTF8 subject DN by default
                true, // Use LDAP DN order by default
                false, // Use CRL Distribution Point on CRL
                false, // CRL Distribution Point on CRL critical
                true, true, // isDoEnforceUniquePublicKeys
                true, // isDoEnforceUniqueDistinguishedName
                false, // isDoEnforceUniqueSubjectDNSerialnumber
                false, // useCertReqHistory
                true, // useUserStorage
                true, // useCertificateStorage
                null // cmpRaAuthSecret
        );
        return x509CaInfo;
    }
    

    
    /** Adds a CA Using ECDSA keys to the database. It also checks that the CA is stored correctly. */
    @Test
    public void test04AddECDSACA() throws Exception {
        boolean ret = false;
        try {
            createEllipticCurveDsaCa();
            CAInfo info = caSession.getCAInfo(admin, "TESTECDSA");
            X509Certificate cert = (X509Certificate) info.getCertificateChain().iterator().next();
            String sigAlg = AlgorithmTools.getSignatureAlgorithm(cert);
            assertEquals(AlgorithmConstants.SIGALG_SHA256_WITH_ECDSA, sigAlg);
            assertTrue("Error in created ca certificate", cert.getSubjectDN().toString().equals("CN=TESTECDSA"));
            assertTrue("Creating CA failed", info.getSubjectDN().equals("CN=TESTECDSA"));
            // Make BC cert instead to make sure the public key is BC provider type (to make our test below easier)
            X509Certificate bccert = (X509Certificate)CertTools.getCertfromByteArray(cert.getEncoded());
            PublicKey pk = bccert.getPublicKey();
            checkECKey(pk);
            ret = true;
        } catch (CAExistsException pee) {
            log.info("CA exists.");
            fail("Creating ECDSA CA failed because CA exists.");
        } finally {
            removeOldCa(TEST_ECDSA_CA_NAME);
        }
        assertTrue("Creating ECDSA CA failed", ret);
    }

    private void checkECKey(PublicKey pk) {
        if (pk instanceof JCEECPublicKey) {
            JCEECPublicKey ecpk = (JCEECPublicKey) pk;
            assertEquals(ecpk.getAlgorithm(), "EC");
            org.bouncycastle.jce.spec.ECParameterSpec spec = ecpk.getParameters();
            assertNotNull("Only ImplicitlyCA curves can have null spec", spec);
        } else if (pk instanceof BCECPublicKey) {
            BCECPublicKey ecpk = (BCECPublicKey) pk;
            assertEquals(ecpk.getAlgorithm(), "EC");
            org.bouncycastle.jce.spec.ECParameterSpec spec = ecpk.getParameters();
            assertNotNull("Only ImplicitlyCA curves can have null spec", spec);
        } else {
            assertTrue("Public key is not EC: "+pk.getClass().getName(), false);
        }        
    }

    /** Adds a CA using ECGOST3410 keys to the database. It also checks that the CA is stored correctly. */
    @Test
    public void test04primAddECGOST3410() throws Exception {
        assumeTrue(AlgorithmTools.isGost3410Enabled());
        boolean ret = false;
        try {
            createECGOST3410Ca();
            CAInfo info = caSession.getCAInfo(admin, CaTestCase.TEST_ECGOST3410_CA_NAME);
            X509Certificate cert = (X509Certificate) info.getCertificateChain().iterator().next();
            String sigAlg = AlgorithmTools.getSignatureAlgorithm(cert);
            assertEquals(AlgorithmConstants.SIGALG_GOST3411_WITH_ECGOST3410, sigAlg);
            assertTrue("Error in created ca certificate", cert.getSubjectDN().toString().equals("CN="+CaTestCase.TEST_ECGOST3410_CA_NAME));
            assertTrue("Creating CA failed", info.getSubjectDN().equals("CN="+CaTestCase.TEST_ECGOST3410_CA_NAME));
            // Make BC cert instead to make sure the public key is BC provider type (to make our test below easier)
            X509Certificate bccert = (X509Certificate)CertTools.getCertfromByteArray(cert.getEncoded());
            PublicKey pk = bccert.getPublicKey();
            checkECGOST3410Key(pk);
            ret = true;
        } catch (CAExistsException pee) {
            log.info("CA exists.");
            fail("Creating ECGOST3410 CA failed because CA exists.");
        } finally {
            removeOldCa(TEST_ECGOST3410_CA_NAME);
        }
        assertTrue("Creating ECGOST3410 CA failed", ret);
    }

    private void checkECGOST3410Key(PublicKey pk) {
        if (pk instanceof BCECGOST3410PublicKey) {
            BCECGOST3410PublicKey gostpk = (BCECGOST3410PublicKey)pk;
            assertEquals("ECGOST3410", gostpk.getAlgorithm());
            org.bouncycastle.jce.spec.ECParameterSpec spec = gostpk.getParameters();
            assertNotNull("GOST3410 public key spec can't be null", spec);
        } else {
            assertTrue("Public key is not GOST3410: "+pk.getClass().getName(), false);
        }
    }
    
    /** Adds a CA using DSTU4510 keys to the database. It also checks that the CA is stored correctly. */
    @Test
    public void test04bisAddDSTU4510() throws Exception {
        log.trace(">" + Thread.currentThread().getStackTrace()[1].getMethodName() + "()");
        assumeTrue(AlgorithmTools.isDstu4145Enabled());
        boolean ret = false;
        try {
            createDSTU4145Ca();
            CAInfo info = caSession.getCAInfo(admin, CaTestCase.TEST_DSTU4145_CA_NAME);
            X509Certificate cert = (X509Certificate) info.getCertificateChain().iterator().next();
            String sigAlg = AlgorithmTools.getSignatureAlgorithm(cert);
            assertEquals(AlgorithmConstants.SIGALG_GOST3411_WITH_DSTU4145, sigAlg);
            assertTrue("Error in created ca certificate", cert.getSubjectDN().toString().equals("CN="+CaTestCase.TEST_DSTU4145_CA_NAME));
            assertTrue("Creating CA failed", info.getSubjectDN().equals("CN="+CaTestCase.TEST_DSTU4145_CA_NAME));
            // Make BC cert instead to make sure the public key is BC provider type (to make our test below easier)
            X509Certificate bccert = (X509Certificate)CertTools.getCertfromByteArray(cert.getEncoded());
            PublicKey pk = bccert.getPublicKey();
            checkDSTU4145Key(pk);
            ret = true;
        } catch (CAExistsException pee) {
            log.info("CA exists.");
            fail("Creating DSTU4145 CA failed because CA exists.");
        } finally {
            removeOldCa(TEST_DSTU4145_CA_NAME);
        }
        assertTrue("Creating DSTU4145 CA failed", ret);
    }
    
    private void checkDSTU4145Key(PublicKey pk) {
        if (pk instanceof BCDSTU4145PublicKey) {
            BCDSTU4145PublicKey dstupk = (BCDSTU4145PublicKey)pk;
            assertEquals("DSTU4145", dstupk.getAlgorithm());
            org.bouncycastle.jce.spec.ECParameterSpec spec = dstupk.getParameters();
            assertNotNull("DSTU4145 public key spec can't be null", spec);
        } else {
            assertTrue("Public key is not DSTU4145: "+pk.getClass().getName(), false);
        }
    }
    
    /** Adds a CA Using ECDSA 'implicitlyCA' keys to the database. It also checks that the CA is stored correctly. */
    @Test
    public void test05AddECDSAImplicitlyCACA() throws Exception {
        log.trace(">test05AddECDSAImplicitlyCACA()");
        boolean ret = false;
        try {
            createEllipticCurveDsaImplicitCa();
            CAInfo info = caSession.getCAInfo(admin, TEST_ECDSA_IMPLICIT_CA_NAME);
            X509Certificate cert = (X509Certificate) info.getCertificateChain().iterator().next();
            assertTrue("Error in created ca certificate", cert.getSubjectDN().toString().equals("CN=TESTECDSAImplicitlyCA"));
            assertTrue("Creating CA failed", info.getSubjectDN().equals("CN=TESTECDSAImplicitlyCA"));
            PublicKey pk = cert.getPublicKey();
            if (pk instanceof JCEECPublicKey) {
                JCEECPublicKey ecpk = (JCEECPublicKey) pk;
                assertEquals(ecpk.getAlgorithm(), "EC");
                ECParameterSpec spec = ecpk.getParameters();
                assertNull("ImplicitlyCA must have null spec, because it should be explicitly set in ejbca.properties", spec);
            } else if (pk instanceof BCECPublicKey) {
                BCECPublicKey ecpk = (BCECPublicKey) pk;
                assertEquals(ecpk.getAlgorithm(), "EC");
                org.bouncycastle.jce.spec.ECParameterSpec spec = ecpk.getParameters();
                assertNull("ImplicitlyCA must have null spec, because it should be explicitly set in ejbca.properties", spec);
            } else {
                assertTrue("Public key is not EC: "+pk.getClass().getName(), false);
            }
            ret = true;
        } catch (CAExistsException pee) {
            log.info("CA exists.");
        } finally {
            removeOldCa(TEST_ECDSA_IMPLICIT_CA_NAME);
        }
        assertTrue("Creating ECDSA ImplicitlyCA CA failed", ret);
        log.trace("<test05AddECDSAImplicitlyCACA()");
    }

    /** Adds a CA using RSA keys to the database. It also checks that the CA is stored correctly. */
    @Test
    public void test06AddRSASha256WithMGF1CA() throws Exception {
        log.trace(">test06AddRSASha256WithMGF1CA()");
        createRSASha256WithMGF1CA();
        CAInfo info = caSession.getCAInfo(admin, TEST_SHA256_WITH_MFG1_CA_NAME);
        X509Certificate cert = (X509Certificate) info.getCertificateChain().iterator().next();
        String sigAlg = AlgorithmTools.getSignatureAlgorithm(cert);
        assertEquals(AlgorithmConstants.SIGALG_SHA256_WITH_RSA_AND_MGF1, sigAlg);
        assertTrue("Error in created ca certificate", cert.getSubjectDN().toString().equals(TEST_SHA256_WITH_MFG1_CA_DN));
        assertTrue("Creating CA failed", info.getSubjectDN().equals(TEST_SHA256_WITH_MFG1_CA_DN));
        PublicKey pk = cert.getPublicKey();
        if (pk instanceof RSAPublicKey) {
            RSAPublicKey rsapk = (RSAPublicKey) pk;
            assertEquals(rsapk.getAlgorithm(), "RSA");
        } else {
            assertTrue("Public key is not RSA", false);
        }
        removeOldCa(TEST_SHA256_WITH_MFG1_CA_NAME);
        log.trace("<test06AddRSASha256WithMGF1CA()");
    }

    @Test
    public void test07AddRSACA4096() throws Exception {
        log.trace(">test07AddRSACA4096()");

        removeOldCa("TESTRSA4096");
        
        boolean ret = false;
        try {
            String dn = CertTools
                    .stringToBCDNString("CN=TESTRSA4096,OU=FooBaaaaaar veeeeeeeery long ou,OU=Another very long very very long ou,O=FoorBar Very looong O,L=Lets ad a loooooooooooooooooong Locality as well,C=SE");
            final CAToken caToken = createCaToken("test07", "1024", AlgorithmConstants.SIGALG_SHA256_WITH_RSA, AlgorithmConstants.SIGALG_SHA1_WITH_RSA);
            // Create and active OSCP CA Service.
            final List<ExtendedCAServiceInfo> extendedcaservices = new ArrayList<ExtendedCAServiceInfo>();
            extendedcaservices.add(new XKMSCAServiceInfo(ExtendedCAServiceInfo.STATUS_INACTIVE, "CN=XKMSCertificate, " + dn, "", "2048",
                    AlgorithmConstants.KEYALGORITHM_RSA));
            X509CAInfo cainfo = new X509CAInfo(
                    dn,
                    "TESTRSA4096",
                    CAConstants.CA_ACTIVE,
                    new Date(),
                    "",
                    CertificateProfileConstants.CERTPROFILE_FIXED_ROOTCA,
                    365,
                    null, // Expiretime
                    CAInfo.CATYPE_X509,
                    CAInfo.SELFSIGNED,
                    (Collection<Certificate>) null,
                    caToken,
                    "JUnit RSA CA, we ned also a very long CA description for this CA, because we want to create a CA Data string that is more than 36000 characters or something like that. All this is because Oracle can not set very long strings with the JDBC provider and we must test that we can handle long CAs",
                    -1, null, null, // PolicyId
                    24 * SimpleTime.MILLISECONDS_PER_HOUR, // CRLPeriod
                    0 * SimpleTime.MILLISECONDS_PER_HOUR, // CRLIssueInterval
                    10 * SimpleTime.MILLISECONDS_PER_HOUR, // CRLOverlapTime
                    0 * SimpleTime.MILLISECONDS_PER_HOUR, // DeltaCRLPeriod
                    new ArrayList<Integer>(), true, // Authority Key Identifier
                    false, // Authority Key Identifier Critical
                    true, // CRL Number
                    false, // CRL Number Critical
                    null, // defaultcrldistpoint
                    null, // defaultcrlissuer
                    null, // defaultocsplocator
                    null, // Authority Information Access
                    null, // defaultfreshestcrl
                    true, // Finish User
                    extendedcaservices, false, // use default utf8 settings
                    new ArrayList<Integer>(), // Approvals Settings
                    1, // Number of Req approvals
                    false, // Use UTF8 subject DN by default
                    true, // Use LDAP DN order by default
                    false, // Use CRL Distribution Point on CRL
                    false, // CRL Distribution Point on CRL critical
                    true, // Include in HealthCheck
                    true, // isDoEnforceUniquePublicKeys
                    true, // isDoEnforceUniqueDistinguishedName
                    false, // isDoEnforceUniqueSubjectDNSerialnumber
                    false, // useCertReqHistory
                    true, // useUserStorage
                    true, // useCertificateStorage
                    null // cmpRaAuthSecret
            );
            caAdminSession.createCA(admin, cainfo);
            CAInfo info = caSession.getCAInfo(admin, "TESTRSA4096");
            X509Certificate cert = (X509Certificate) info.getCertificateChain().iterator().next();
            String sigAlg = AlgorithmTools.getSignatureAlgorithm(cert);
            assertEquals(AlgorithmConstants.SIGALG_SHA256_WITH_RSA, sigAlg);
            assertTrue("Error in created ca certificate", CertTools.stringToBCDNString(cert.getSubjectDN().toString()).equals(dn));
            assertTrue("Creating CA failed", info.getSubjectDN().equals(dn));
            // Normal order
            assertEquals(
                    cert.getSubjectX500Principal().getName(),
                    "C=SE,L=Lets ad a loooooooooooooooooong Locality as well,O=FoorBar Very looong O,OU=Another very long very very long ou,OU=FooBaaaaaar veeeeeeeery long ou,CN=TESTRSA4096");
            PublicKey pk = cert.getPublicKey();
            if (pk instanceof RSAPublicKey) {
                RSAPublicKey rsapk = (RSAPublicKey) pk;
                assertEquals(rsapk.getAlgorithm(), "RSA");
            } else {
                assertTrue("Public key is not EC", false);
            }
            ret = true;
        } catch (CAExistsException pee) {
            log.info("CA exists.");
        } finally {
            removeOldCa("TESTRSA4096");
        }
        assertTrue("Creating RSA CA 4096 failed", ret);
        log.trace("<test07AddRSACA4096()");
    }

    @Test
    public void test08AddRSACAReverseDN() throws Exception {
        log.trace(">test08AddRSACAReverseDN()");
        removeOldCa(TEST_RSA_REVERSE_CA_NAME);
        try {
            createTestRSAReverseCa(admin);
            CAInfo info = caSession.getCAInfo(admin, TEST_RSA_REVERSE_CA_NAME);
            X509Certificate cert = (X509Certificate) info.getCertificateChain().iterator().next();
            String sigAlg = AlgorithmTools.getSignatureAlgorithm(cert);
            assertEquals(AlgorithmConstants.SIGALG_SHA1_WITH_RSA, sigAlg);
            assertEquals("Error in created ca certificate", CertTools.stringToBCDNString(cert.getSubjectDN().toString()), TEST_RSA_REVSERSE_CA_DN);
            assertTrue("Creating CA failed", info.getSubjectDN().equals(TEST_RSA_REVSERSE_CA_DN));
            // reverse order
            assertEquals(cert.getSubjectX500Principal().getName(), "CN=TESTRSAReverse,OU=BarFoo,O=FooBar,C=SE");
            assertEquals("Public key is not RSA", AlgorithmConstants.KEYALGORITHM_RSA, AlgorithmTools.getKeyAlgorithm(cert.getPublicKey()));
        } catch (CAExistsException pee) {
            log.info("CA exists.");
            fail("Creating RSA CA reverse failed");
        } finally {
            removeOldCa(TEST_RSA_REVERSE_CA_NAME);
        }
        log.trace("<test08AddRSACAReverseDN()");
    }

    @Test
    public void test09AddCVCCARSA() throws Exception {
        removeOldCa("TESTDV-D");
        removeOldCa("TESTCVCA");
        removeOldCa("TESTDV-F");
        certificateProfileSession.removeCertificateProfile(admin, "TESTCVCDV");
        final List<ExtendedCAServiceInfo> extendedcaservices = new ArrayList<ExtendedCAServiceInfo>(0);
        String dvddn = "CN=TESTDV-D,C=SE";
        String dvdcaname = "TESTDV-D";
        String dvfdn = "CN=TESTDV-F,C=FI";
        String dvfcaname = "TESTDV-F";
        CAInfo dvdcainfo = null; // to be used for renewal
        CAInfo cvcainfo = null; // to be used for making request
        // Create a root CVCA
        try {
            createDefaultCvcRsaCA();
            cvcainfo = caSession.getCAInfo(admin, TEST_CVC_RSA_CA_NAME);
            assertEquals(CAInfo.CATYPE_CVC, cvcainfo.getCAType());
            Certificate cert = (Certificate) cvcainfo.getCertificateChain().iterator().next();
            String sigAlg = AlgorithmTools.getSignatureAlgorithm(cert);
            assertEquals(AlgorithmConstants.SIGALG_SHA256_WITH_RSA_AND_MGF1, sigAlg);
            assertEquals("CVC", cert.getType());
            assertEquals(TEST_CVC_RSA_CA_DN, CertTools.getSubjectDN(cert));
            assertEquals(TEST_CVC_RSA_CA_DN, CertTools.getIssuerDN(cert));
            assertEquals(TEST_CVC_RSA_CA_DN, cvcainfo.getSubjectDN());
            final PublicKey pk = cert.getPublicKey();
            assertEquals("Public key is not RSA", AlgorithmConstants.KEYALGORITHM_RSA, AlgorithmTools.getKeyAlgorithm(pk));
            assertEquals("Public key has unexpected modulus", "1024", AlgorithmTools.getKeySpecification(pk));
            assertTrue("CA is not valid for the specified duration.",
                    CertTools.getNotAfter(cert).after(new Date(new Date().getTime() + 10 * 364 * 24 * 60 * 60 * 1000L))
                            && CertTools.getNotAfter(cert).before(new Date(new Date().getTime() + 10 * 366 * 24 * 60 * 60 * 1000L)));
            // Check role
            CardVerifiableCertificate cvcert = (CardVerifiableCertificate) cert;
            String role = cvcert.getCVCertificate().getCertificateBody().getAuthorizationTemplate().getAuthorizationField().getRole().name();
            assertEquals("SETESTCVCA00000", cvcert.getCVCertificate().getCertificateBody().getHolderReference().getConcatenated());
            assertEquals("CVCA", role);
        } catch (CAExistsException pee) {
            fail("CA exists.");
        }
        // Create a Sub DV domestic
        final CAToken caToken = createCaToken("test09", "1024", AlgorithmConstants.SIGALG_SHA256_WITH_RSA_AND_MGF1, AlgorithmConstants.SIGALG_SHA256_WITH_RSA_AND_MGF1);
        try {
            // Create a Certificate profile
            CertificateProfile profile = new CertificateProfile(CertificateProfileConstants.CERTPROFILE_FIXED_SUBCA);
            certificateProfileSession.addCertificateProfile(admin, "TESTCVCDV", profile);
            int profileid = certificateProfileSession.getCertificateProfileId("TESTCVCDV");
            CVCCAInfo cvccainfo = new CVCCAInfo(dvddn, dvdcaname, CAConstants.CA_ACTIVE, new Date(), profileid, 3650, null, // Expiretime
                    CAInfo.CATYPE_CVC, TEST_CVC_RSA_CA_DN.hashCode(), null, caToken, "JUnit CVC CA", -1, null, 24, // CRLPeriod
                    0, // CRLIssueInterval
                    10, // CRLOverlapTime
                    10, // Delta CRL period
                    new ArrayList<Integer>(), // CRL publishers
                    true, // Finish User
                    extendedcaservices, new ArrayList<Integer>(), // Approvals Settings
                    1, // Number of Req approvals
                    true, // Include in health check
                    true, // isDoEnforceUniquePublicKeys
                    true, // isDoEnforceUniqueDistinguishedName
                    false, // isDoEnforceUniqueSubjectDNSerialnumber
                    false, // useCertReqHistory
                    true, // useUserStorage
                    true // useCertificateStorage
            );
            caAdminSession.createCA(admin, cvccainfo);
            dvdcainfo = caSession.getCAInfo(admin, dvdcaname);
            assertEquals(CAInfo.CATYPE_CVC, dvdcainfo.getCAType());
            Certificate cert = (Certificate) dvdcainfo.getCertificateChain().iterator().next();
            assertEquals("CVC", cert.getType());
            assertEquals(CertTools.getSubjectDN(cert), dvddn);
            assertEquals(CertTools.getIssuerDN(cert), TEST_CVC_RSA_CA_DN);
            assertEquals(dvdcainfo.getSubjectDN(), dvddn);
            PublicKey pk = cert.getPublicKey();
            assertEquals("Public key is not RSA", AlgorithmConstants.KEYALGORITHM_RSA, AlgorithmTools.getKeyAlgorithm(pk));
            assertEquals("Public key has unexpected modulus", "1024", AlgorithmTools.getKeySpecification(pk));
            assertTrue("CA is not valid for the specified duration.",
                    CertTools.getNotAfter(cert).after(new Date(new Date().getTime() + 10 * 364 * 24 * 60 * 60 * 1000L))
                            && CertTools.getNotAfter(cert).before(new Date(new Date().getTime() + 10 * 366 * 24 * 60 * 60 * 1000L)));
            // Check role
            CardVerifiableCertificate cvcert = (CardVerifiableCertificate) cert;
            assertEquals("SETESTDV-D00000", cvcert.getCVCertificate().getCertificateBody().getHolderReference().getConcatenated());
            String role = cvcert.getCVCertificate().getCertificateBody().getAuthorizationTemplate().getAuthorizationField().getRole().name();
            assertEquals("DV_D", role);
            String accessRights = cvcert.getCVCertificate().getCertificateBody().getAuthorizationTemplate().getAuthorizationField().getAccessRight()
                    .name();
            assertEquals("READ_ACCESS_DG3_AND_DG4", accessRights);
        } catch (CAExistsException pee) {
            fail("CA exists.");
        }
        // Create a Sub DV foreign
        try {
            CVCCAInfo cvccainfo = new CVCCAInfo(dvfdn, dvfcaname, CAConstants.CA_ACTIVE, new Date(), CertificateProfileConstants.CERTPROFILE_FIXED_SUBCA, 3650, null, // Expiretime
                    CAInfo.CATYPE_CVC, TEST_CVC_RSA_CA_DN.hashCode(), null, caToken, "JUnit CVC CA", -1, null, 24, // CRLPeriod
                    0, // CRLIssueInterval
                    10, // CRLOverlapTime
                    10, // Delta CRL period
                    new ArrayList<Integer>(), // CRL publishers
                    true, // Finish User
                    extendedcaservices, new ArrayList<Integer>(), // Approvals Settings
                    1, // Number of Req approvals
                    true, // Include in health check
                    true, // isDoEnforceUniquePublicKeys
                    true, // isDoEnforceUniqueDistinguishedName
                    false, // isDoEnforceUniqueSubjectDNSerialnumber
                    false, // useCertReqHistory
                    true, // useUserStorage
                    true // useCertificateStorage
            );
            caAdminSession.createCA(admin, cvccainfo);
            CAInfo info = caSession.getCAInfo(admin, dvfcaname);
            assertEquals(CAInfo.CATYPE_CVC, info.getCAType());
            Certificate cert = (Certificate) info.getCertificateChain().iterator().next();
            assertEquals("CVC", cert.getType());
            assertEquals(CertTools.getSubjectDN(cert), dvfdn);
            assertEquals(CertTools.getIssuerDN(cert), TEST_CVC_RSA_CA_DN);
            assertEquals(info.getSubjectDN(), dvfdn);
            PublicKey pk = cert.getPublicKey();
            assertEquals("Public key is not RSA", AlgorithmConstants.KEYALGORITHM_RSA, AlgorithmTools.getKeyAlgorithm(pk));
            assertEquals("Public key has unexpected modulus", "1024", AlgorithmTools.getKeySpecification(pk));
            assertTrue("CA is not valid for the specified duration.",
                    CertTools.getNotAfter(cert).after(new Date(new Date().getTime() + 10 * 364 * 24 * 60 * 60 * 1000L))
                            && CertTools.getNotAfter(cert).before(new Date(new Date().getTime() + 10 * 366 * 24 * 60 * 60 * 1000L)));
            // Check role
            CardVerifiableCertificate cvcert = (CardVerifiableCertificate) cert;
            assertEquals("FITESTDV-F00000", cvcert.getCVCertificate().getCertificateBody().getHolderReference().getConcatenated());
            String role = cvcert.getCVCertificate().getCertificateBody().getAuthorizationTemplate().getAuthorizationField().getRole().name();
            assertEquals("DV_F", role);
        } catch (CAExistsException pee) {
            fail("Creating CVC CAs failed");
        }
        // Test to renew a CVC CA using a different access right
        CertificateProfile profile = certificateProfileSession.getCertificateProfile("TESTCVCDV");
        profile.setCVCAccessRights(CertificateProfile.CVC_ACCESS_DG3);
        certificateProfileSession.changeCertificateProfile(admin, "TESTCVCDV", profile);
        int caid = dvdcainfo.getCAId();
        caAdminSession.renewCA(admin, caid, false, null, true);
        dvdcainfo = caSession.getCAInfo(admin, dvdcaname);
        assertEquals(CAInfo.CATYPE_CVC, dvdcainfo.getCAType());
        Certificate cert = (Certificate) dvdcainfo.getCertificateChain().iterator().next();
        assertEquals("CVC", cert.getType());
        assertEquals(CertTools.getSubjectDN(cert), dvddn);
        assertEquals(CertTools.getIssuerDN(cert), TEST_CVC_RSA_CA_DN);
        assertEquals(dvdcainfo.getSubjectDN(), dvddn);
        // It's not possible to check the time for renewal of a CVC CA since the resolution of validity is only days.
        // The only way is to generate a certificate with different access rights in it
        CardVerifiableCertificate cvcert = (CardVerifiableCertificate) cert;
        String role = cvcert.getCVCertificate().getCertificateBody().getAuthorizationTemplate().getAuthorizationField().getRole().name();
        assertEquals("DV_D", role);
        String accessRights = cvcert.getCVCertificate().getCertificateBody().getAuthorizationTemplate().getAuthorizationField().getAccessRight()
                .name();
        assertEquals("READ_ACCESS_DG3", accessRights);
        // Holder should not have changed since we never generated new keys
        assertEquals("SETESTDV-D00000", cvcert.getCVCertificate().getCertificateBody().getHolderReference().getConcatenated());

        // Make a certificate request from a CVCA
        Collection<Certificate> cachain = cvcainfo.getCertificateChain();
        assertEquals(1, cachain.size());
        Certificate cert1 = cachain.iterator().next();
        CardVerifiableCertificate cvcert1 = (CardVerifiableCertificate) cert1;
        assertEquals("SETESTCVCA00000", cvcert1.getCVCertificate().getCertificateBody().getHolderReference().getConcatenated());
        byte[] request = caAdminSession.makeRequest(admin, cvcainfo.getCAId(), cachain, "SETESTCVCA00001");
        CVCObject obj = CertificateParser.parseCVCObject(request);
        // We should have created an authenticated request signed by the the old key,
        // but since the CVCA is not renewed, and no old key exists, it will be
        // an un-authenticated request instead.
        CVCAuthenticatedRequest authReqCVCA = (CVCAuthenticatedRequest) obj;
        assertEquals("SETESTCVCA00000", authReqCVCA.getRequest().getCertificateBody().getHolderReference().getConcatenated());
        assertEquals("SETESTCVCA00000", authReqCVCA.getRequest().getCertificateBody().getAuthorityReference().getConcatenated());

        // Make a certificate request from a DV, regenerating keys
        cachain = dvdcainfo.getCertificateChain();
        request = caAdminSession.makeRequest(admin, dvdcainfo.getCAId(), cachain, null);
        obj = CertificateParser.parseCVCObject(request);
        // We should have created an authenticated request signed by the old certificate
        CVCAuthenticatedRequest authReqDVDCA = (CVCAuthenticatedRequest) obj;
        assertEquals("SETESTDV-D00001", authReqDVDCA.getRequest().getCertificateBody().getHolderReference().getConcatenated());
        // This request is made from the DV targeted for the DV, so the old DV
        // certificate will be the holder ref.
        // Normally you would target an external CA, and thus send in it's
        // cachain. The caRef would be the external CAs holderRef.
        assertEquals("SETESTDV-D00000", authReqDVDCA.getRequest().getCertificateBody().getAuthorityReference().getConcatenated());

        // Get the DVs certificate request signed by the CVCA
        byte[] authrequest = caAdminSession.createAuthCertSignRequest(admin, cvcainfo.getCAId(), request);
        CVCObject parsedObject = CertificateParser.parseCVCObject(authrequest);
        authReqDVDCA = (CVCAuthenticatedRequest) parsedObject;
        assertEquals("SETESTDV-D00001", authReqDVDCA.getRequest().getCertificateBody().getHolderReference().getConcatenated());
        assertEquals("SETESTDV-D00000", authReqDVDCA.getRequest().getCertificateBody().getAuthorityReference().getConcatenated());
        assertEquals("SETESTCVCA00000", authReqDVDCA.getAuthorityReference().getConcatenated());
        // Get the DVs certificate request signed by the CVCA creating a SubCA certificate.
        EndEntityInformation endEntityInformation = new EndEntityInformation(/*dvdcainfo.getName()*/ "SYSTEMCA", dvdcainfo.getSubjectDN(), cvcainfo.getCAId(),
                null, null, EndEntityTypes.ENDUSER.toEndEntityType(), SecConst.EMPTY_ENDENTITYPROFILE, CertificateProfileConstants.CERTPROFILE_FIXED_SUBCA,
                SecConst.TOKEN_SOFT_BROWSERGEN, 0, null);
        endEntityInformation.setPassword("foo123");
        final CertificateCreateSessionRemote certificateCreateSession = EjbRemoteHelper.INSTANCE.getRemoteSession(CertificateCreateSessionRemote.class);
        final CVCRequestMessage cvcRequestMessage = new CVCRequestMessage(request);
        cvcRequestMessage.setUsername("SYSTEMCA");
        cvcRequestMessage.setPassword("foo123");
        // Yes it says X509ResponseMessage, but for CVC it means it just contains the binary certificate blob
        final ResponseMessage responseMessage = certificateCreateSession.createCertificate(admin, endEntityInformation, cvcRequestMessage, X509ResponseMessage.class);
        final byte[] newDvCertBytes = CertTools.getCertfromByteArray(responseMessage.getResponseMessage()).getEncoded();
        final CVCertificate newDvCert = (CVCertificate) CertificateParser.parseCVCObject(newDvCertBytes);
        assertEquals("SETESTCVCA00000", newDvCert.getCertificateBody().getAuthorityReference().getConcatenated());
        assertEquals("SETESTDV-D00001", newDvCert.getCertificateBody().getHolderReference().getConcatenated());
        // We would expect the same result if the authenticated version of the request is used
        final CVCRequestMessage cvcRequestMessage2 = new CVCRequestMessage(authrequest);
        cvcRequestMessage.setUsername("SYSTEMCA");
        cvcRequestMessage.setPassword("foo123");
        final ResponseMessage responseMessage2 = certificateCreateSession.createCertificate(admin, endEntityInformation, cvcRequestMessage2, X509ResponseMessage.class);
        final byte[] newDvCertBytes2 = CertTools.getCertfromByteArray(responseMessage2.getResponseMessage()).getEncoded();
        final CVCertificate newDvCert2 = (CVCertificate) CertificateParser.parseCVCObject(newDvCertBytes2);
        assertEquals("SETESTCVCA00000", newDvCert2.getCertificateBody().getAuthorityReference().getConcatenated());
        assertEquals("SETESTDV-D00001", newDvCert2.getCertificateBody().getHolderReference().getConcatenated());
        // Request renewal, but since we never uploaded the signed response from the "external" RootCA it will re-use the next-key
        caAdminSession.renewCA(admin, dvdcainfo.getCAId(), true, null, true);
        dvdcainfo = caSession.getCAInfo(admin, dvdcaname);
        assertEquals(CAInfo.CATYPE_CVC, dvdcainfo.getCAType());
        cert = (Certificate) dvdcainfo.getCertificateChain().iterator().next();
        assertEquals("CVC", cert.getType());
        assertEquals(CertTools.getSubjectDN(cert), dvddn);
        assertEquals(CertTools.getIssuerDN(cert), TEST_CVC_RSA_CA_DN);
        assertEquals(dvdcainfo.getSubjectDN(), dvddn);
        cvcert = (CardVerifiableCertificate) cert;
        role = cvcert.getCVCertificate().getCertificateBody().getAuthorizationTemplate().getAuthorizationField().getRole().name();
        assertEquals("DV_D", role);
        String holderRef = cvcert.getCVCertificate().getCertificateBody().getHolderReference().getConcatenated();
        // Sequence should be the same as when we made the request and requested key generation
        assertEquals("SETESTDV-D00001", holderRef);
        removeOldCa("TESTDV-D");
        removeOldCa("TESTCVCA");
        removeOldCa("TESTDV-F");
    } // test09AddCVCCARSA

    /**
     * 
     * @throws Exception
     */
    @Test
    public void test10AddCVCCAECC() throws Exception {
        log.trace(">" + Thread.currentThread().getStackTrace()[1].getMethodName() + "()");
        removeOldCa("TESTCVCAECC");
        removeOldCa("TESTDVECC-D");
        removeOldCa("TESTDVECC-F");
        // ECDSA for encryption???
        final CAToken caToken = createCaToken("test10", "secp256r1", AlgorithmConstants.SIGALG_SHA256_WITH_ECDSA, AlgorithmConstants.SIGALG_SHA256_WITH_ECDSA);
        final List<ExtendedCAServiceInfo> extendedcaservices = new ArrayList<ExtendedCAServiceInfo>(0);
        String dvfdn = "CN=TDVEC-F,C=FI";
        String dvfcaname = "TESTDVECC-F";
        CAInfo dvdcainfo = null; // to be used for renewal
        CAInfo cvcainfo = null; // to be used for making request
        // Create a root CVCA
        try {
            createDefaultCvcEccCa();
            cvcainfo = caSession.getCAInfo(admin, TEST_CVC_ECC_CA_NAME);
            assertEquals(CAInfo.CATYPE_CVC, cvcainfo.getCAType());
            Certificate cert = (Certificate) cvcainfo.getCertificateChain().iterator().next();
            String sigAlg = AlgorithmTools.getSignatureAlgorithm(cert);
            assertEquals(AlgorithmConstants.SIGALG_SHA256_WITH_ECDSA, sigAlg);
            assertEquals("CVC", cert.getType());
            assertEquals(TEST_CVC_ECC_CA_DN, CertTools.getSubjectDN(cert));
            assertEquals(TEST_CVC_ECC_CA_DN, CertTools.getIssuerDN(cert));
            assertEquals(TEST_CVC_ECC_CA_DN, cvcainfo.getSubjectDN());
            PublicKey pk = cert.getPublicKey();
            if (pk instanceof ECPublicKey) {
                ECPublicKey epk = (ECPublicKey) pk;
                assertEquals(epk.getAlgorithm(), "ECDSA");
                int len = KeyTools.getKeyLength(epk);
                assertEquals(256, len);
            } else {
                assertTrue("Public key is not ECC", false);
            }
            assertTrue("CA is not valid for the specified duration.",
                    CertTools.getNotAfter(cert).after(new Date(new Date().getTime() + 10 * 364 * 24 * 60 * 60 * 1000L))
                            && CertTools.getNotAfter(cert).before(new Date(new Date().getTime() + 10 * 366 * 24 * 60 * 60 * 1000L)));
            // Check role
            CardVerifiableCertificate cvcert = (CardVerifiableCertificate) cert;
            String role = cvcert.getCVCertificate().getCertificateBody().getAuthorizationTemplate().getAuthorizationField().getRole().name();
            assertEquals("SETCVCAEC00000", cvcert.getCVCertificate().getCertificateBody().getHolderReference().getConcatenated());
            assertEquals("CVCA", role);
        } catch (CAExistsException pee) {
            fail("CA exists.");
        }
        // Create a Sub DV domestic
        try {
            createDefaultCvcEccCaDomestic();
            dvdcainfo = caSession.getCAInfo(admin, TEST_CVC_ECC_DOCUMENT_VERIFIER_NAME);
            assertEquals(CAInfo.CATYPE_CVC, dvdcainfo.getCAType());

            Certificate cert = (Certificate) dvdcainfo.getCertificateChain().iterator().next();
            assertEquals("CVC", cert.getType());
            assertEquals(CertTools.getSubjectDN(cert), TEST_CVC_ECC_DOCUMENT_VERIFIER_DN);
            assertEquals(CertTools.getIssuerDN(cert), TEST_CVC_ECC_CA_DN);
            assertEquals(dvdcainfo.getSubjectDN(), TEST_CVC_ECC_DOCUMENT_VERIFIER_DN);
            PublicKey pk = cert.getPublicKey();
            if (pk instanceof ECPublicKey) {
                ECPublicKey epk = (ECPublicKey) pk;
                assertEquals(epk.getAlgorithm(), "ECDSA");
                int len = KeyTools.getKeyLength(epk);
                assertEquals(0, len); // the DVCA does not include all EC
                // parameters in the public key, so we
                // don't know the key length
            } else {
                assertTrue("Public key is not ECC", false);
            }
            assertTrue("CA is not valid for the specified duration.",
                    CertTools.getNotAfter(cert).after(new Date(new Date().getTime() + 10 * 364 * 24 * 60 * 60 * 1000L))
                            && CertTools.getNotAfter(cert).before(new Date(new Date().getTime() + 10 * 366 * 24 * 60 * 60 * 1000L)));
            // Check role
            CardVerifiableCertificate cvcert = (CardVerifiableCertificate) cert;
            assertEquals("SETDVEC-D00000", cvcert.getCVCertificate().getCertificateBody().getHolderReference().getConcatenated());
            String role = cvcert.getCVCertificate().getCertificateBody().getAuthorizationTemplate().getAuthorizationField().getRole().name();
            assertEquals("DV_D", role);
            String accessRights = cvcert.getCVCertificate().getCertificateBody().getAuthorizationTemplate().getAuthorizationField().getAccessRight()
                    .name();
            assertEquals("READ_ACCESS_DG3_AND_DG4", accessRights);
        } catch (CAExistsException pee) {
            fail("CA exists.");
        }
        // Create a Sub DV foreign
        try {

            CVCCAInfo cvccainfo = new CVCCAInfo(dvfdn, dvfcaname, CAConstants.CA_ACTIVE, new Date(), CertificateProfileConstants.CERTPROFILE_FIXED_SUBCA, 3650, null, // Expiretime
                    CAInfo.CATYPE_CVC, TEST_CVC_ECC_CA_DN.hashCode(), null, caToken, "JUnit CVC CA", -1, null, 24, // CRLPeriod
                    0, // CRLIssueInterval
                    10, // CRLOverlapTime
                    10, // Delta CRL period
                    new ArrayList<Integer>(), // CRL publishers
                    true, // Finish User
                    extendedcaservices, new ArrayList<Integer>(), // Approvals Settings
                    1, // Number of Req approvals
                    true, // Include in health check
                    true, // isDoEnforceUniquePublicKeys
                    true, // isDoEnforceUniqueDistinguishedName
                    false, // isDoEnforceUniqueSubjectDNSerialnumber
                    false, // useCertReqHistory
                    true, // useUserStorage
                    true // useCertificateStorage
            );

            caAdminSession.createCA(admin, cvccainfo);

            CAInfo info = caSession.getCAInfo(admin, dvfcaname);
            assertEquals(CAInfo.CATYPE_CVC, info.getCAType());

            Certificate cert = (Certificate) info.getCertificateChain().iterator().next();
            assertEquals("CVC", cert.getType());
            assertEquals(CertTools.getSubjectDN(cert), dvfdn);
            assertEquals(CertTools.getIssuerDN(cert), TEST_CVC_ECC_CA_DN);
            assertEquals(info.getSubjectDN(), dvfdn);
            PublicKey pk = cert.getPublicKey();
            if (pk instanceof ECPublicKey) {
                ECPublicKey epk = (ECPublicKey) pk;
                assertEquals(epk.getAlgorithm(), "ECDSA");
                int len = KeyTools.getKeyLength(epk);
                assertEquals(0, len); // the DVCA does not include all EC
                // parameters in the public key, so we
                // don't know the key length
            } else {
                assertTrue("Public key is not ECC", false);
            }
            assertTrue("CA is not valid for the specified duration.",
                    CertTools.getNotAfter(cert).after(new Date(new Date().getTime() + 10 * 364 * 24 * 60 * 60 * 1000L))
                            && CertTools.getNotAfter(cert).before(new Date(new Date().getTime() + 10 * 366 * 24 * 60 * 60 * 1000L)));
            // Check role
            CardVerifiableCertificate cvcert = (CardVerifiableCertificate) cert;
            assertEquals("FITDVEC-F00000", cvcert.getCVCertificate().getCertificateBody().getHolderReference().getConcatenated());
            String role = cvcert.getCVCertificate().getCertificateBody().getAuthorizationTemplate().getAuthorizationField().getRole().name();
            assertEquals("DV_F", role);
        } catch (CAExistsException pee) {
            fail("Creating CVC CAs failed");
        }
        // Test to renew a CVC CA
        dvdcainfo = caSession.getCAInfo(admin, TEST_CVC_ECC_DOCUMENT_VERIFIER_NAME);
        Certificate cert = (Certificate) dvdcainfo.getCertificateChain().iterator().next();
        // Verify that fingerprint and CA fingerprint is handled correctly
        CertificateInfo certInfo = certificateStoreSession.getCertificateInfo(CertTools.getFingerprintAsString(cert));
        assertFalse(certInfo.getFingerprint().equals(certInfo.getCAFingerprint()));
        int caid = dvdcainfo.getCAId();
        //caAdminSession.renewCA(admin, caid, null, false, null);
        assertEquals("SETDVEC-D00000", ((CardVerifiableCertificate) cert).getCVCertificate().getCertificateBody().getHolderReference().getConcatenated());
        caAdminSession.renewCA(admin, caid, false, null, false);
        dvdcainfo = caSession.getCAInfo(admin, TEST_CVC_ECC_DOCUMENT_VERIFIER_NAME);
        cert = (Certificate) dvdcainfo.getCertificateChain().iterator().next();
        assertEquals("Holder changed dispite re-use of singing key.", "SETDVEC-D00000", ((CardVerifiableCertificate) cert).getCVCertificate().getCertificateBody().getHolderReference().getConcatenated());
        assertEquals(CAInfo.CATYPE_CVC, dvdcainfo.getCAType());
        assertEquals("CVC", cert.getType());
        assertEquals(CertTools.getSubjectDN(cert), TEST_CVC_ECC_DOCUMENT_VERIFIER_DN);
        assertEquals(CertTools.getIssuerDN(cert), TEST_CVC_ECC_CA_DN);
        assertEquals(dvdcainfo.getSubjectDN(), TEST_CVC_ECC_DOCUMENT_VERIFIER_DN);
        // Verify that fingerprint and CA fingerprint is handled correctly
        certInfo = certificateStoreSession.getCertificateInfo(CertTools.getFingerprintAsString(cert));
        assertFalse(certInfo.getFingerprint().equals(certInfo.getCAFingerprint()));
        // It's not possible to check the time for renewal of a CVC CA since the
        // resolution of validity is only days.
        // The only way is to generate a certificate with different access
        // rights in it
        CardVerifiableCertificate cvcert = (CardVerifiableCertificate) cert;
        String role = cvcert.getCVCertificate().getCertificateBody().getAuthorizationTemplate().getAuthorizationField().getRole().name();
        assertEquals("DV_D", role);
        String accessRights = cvcert.getCVCertificate().getCertificateBody().getAuthorizationTemplate().getAuthorizationField().getAccessRight()
                .name();
        assertEquals("READ_ACCESS_DG3_AND_DG4", accessRights);

        // Make a certificate request from a DV, regenerating keys
        Collection<Certificate> cachain = dvdcainfo.getCertificateChain();
        byte[] request = caAdminSession.makeRequest(admin, dvdcainfo.getCAId(), cachain, null);
        CVCObject obj = CertificateParser.parseCVCObject(request);
        // We should have created an authenticated request signed by the old certificate
        CVCAuthenticatedRequest authreq = (CVCAuthenticatedRequest) obj;
        CVCertificate reqcert = authreq.getRequest();
        assertEquals("SETDVEC-D00001", reqcert.getCertificateBody().getHolderReference().getConcatenated());
        // This request is made from the DV targeted for the DV, so the old DV
        // certificate will be the holder ref.
        // Normally you would target an external CA, and thus send in it's
        // cachain. The caRef would be the external CAs holderRef.
        assertEquals("SETDVEC-D00000", reqcert.getCertificateBody().getAuthorityReference().getConcatenated());

        // Get the DVs certificate request signed by the CVCA
        byte[] authrequest = caAdminSession.createAuthCertSignRequest(admin, cvcainfo.getCAId(), request);
        CVCObject parsedObject = CertificateParser.parseCVCObject(authrequest);
        authreq = (CVCAuthenticatedRequest) parsedObject;
        assertEquals("SETDVEC-D00001", authreq.getRequest().getCertificateBody().getHolderReference().getConcatenated());
        assertEquals("SETDVEC-D00000", authreq.getRequest().getCertificateBody().getAuthorityReference().getConcatenated());
        assertEquals("SETCVCAEC00000", authreq.getAuthorityReference().getConcatenated());

        /*
        // Get the DVs certificate request signed by the CVCA creating a link certificate.
        // Passing in a request without authrole should return a regular authenticated request though.
        authrequest = caAdminSession.signRequest(admin, cvcainfo.getCAId(), request, false, true);
        parsedObject = CertificateParser.parseCVCObject(authrequest);
        authreq = (CVCAuthenticatedRequest) parsedObject;
*/
        // Pass in a certificate instead
/*
        CardVerifiableCertificate dvdcert = (CardVerifiableCertificate) cachain.iterator().next();
        authrequest = caAdminSession.signRequest(admin, cvcainfo.getCAId(), dvdcert.getEncoded(), false, true);
        parsedObject = CertificateParser.parseCVCObject(authrequest);
        CVCertificate linkcert = (CVCertificate) parsedObject;
        assertEquals("SETCVCAEC00001", linkcert.getCertificateBody().getAuthorityReference().getConcatenated());
        assertEquals("SETDVEC-D00001", linkcert.getCertificateBody().getHolderReference().getConcatenated());
*/
        // Get the DVs certificate request signed by the CVCA creating a SubCA certificate.
        EndEntityInformation endEntityInformation = new EndEntityInformation(/*dvdcainfo.getName()*/ "SYSTEMCA", dvdcainfo.getSubjectDN(), cvcainfo.getCAId(),
                null, null, EndEntityTypes.ENDUSER.toEndEntityType(), SecConst.EMPTY_ENDENTITYPROFILE, CertificateProfileConstants.CERTPROFILE_FIXED_SUBCA,
                SecConst.TOKEN_SOFT_BROWSERGEN, 0, null);
        endEntityInformation.setPassword("foo123");
        final CertificateCreateSessionRemote certificateCreateSession = EjbRemoteHelper.INSTANCE.getRemoteSession(CertificateCreateSessionRemote.class);
        final CVCRequestMessage cvcRequestMessage = new CVCRequestMessage(authrequest);
        cvcRequestMessage.setUsername("SYSTEMCA");
        cvcRequestMessage.setPassword("foo123");
        // Yes it says X509ResponseMessage, but for CVC it means it just contains the binary certificate blob
        final ResponseMessage responseMessage = certificateCreateSession.createCertificate(admin, endEntityInformation, cvcRequestMessage, X509ResponseMessage.class);
        final byte[] newDvCertBytes = CertTools.getCertfromByteArray(responseMessage.getResponseMessage()).getEncoded();
        final CVCertificate newDvCert = (CVCertificate) CertificateParser.parseCVCObject(newDvCertBytes);


        /*
        final RequestMessage reqMsg = new CVCRequestMessage(dvdcert.getEncoded());
        final byte[] newDvCertBytes = caAdminSession.processRequest(admin, cvcainfo, reqMsg).getResponseMessage();
        final CVCertificate newDvCert = (CVCertificate) CertificateParser.parseCVCObject(newDvCertBytes);
        */
        assertEquals("SETCVCAEC00000", newDvCert.getCertificateBody().getAuthorityReference().getConcatenated());
        assertEquals("SETDVEC-D00001", newDvCert.getCertificateBody().getHolderReference().getConcatenated());

        // Request renewal, but since we never uploaded the signed response from the "external" RootCA it will re-use the next-key
        caAdminSession.renewCA(admin, caid, true, null, true);
        dvdcainfo = caSession.getCAInfo(admin, TEST_CVC_ECC_DOCUMENT_VERIFIER_NAME);
        assertEquals(CAInfo.CATYPE_CVC, dvdcainfo.getCAType());
        cert = (Certificate) dvdcainfo.getCertificateChain().iterator().next();
        assertEquals("CVC", cert.getType());
        assertEquals(CertTools.getSubjectDN(cert), TEST_CVC_ECC_DOCUMENT_VERIFIER_DN);
        assertEquals(CertTools.getIssuerDN(cert), TEST_CVC_ECC_CA_DN);
        assertEquals(dvdcainfo.getSubjectDN(), TEST_CVC_ECC_DOCUMENT_VERIFIER_DN);
        cvcert = (CardVerifiableCertificate) cert;
        role = cvcert.getCVCertificate().getCertificateBody().getAuthorizationTemplate().getAuthorizationField().getRole().name();
        assertEquals("DV_D", role);
        String holderRef = cvcert.getCVCertificate().getCertificateBody().getHolderReference().getConcatenated();
        // Sequence must have been updated with 1
        assertEquals("SETDVEC-D00001", holderRef);

        // Make a certificate request from a CVCA
        cachain = cvcainfo.getCertificateChain();
        assertEquals(1, cachain.size());
        Certificate cert1 = (Certificate) cachain.iterator().next();
        CardVerifiableCertificate cvcert1 = (CardVerifiableCertificate) cert1;
        assertEquals("SETCVCAEC00000", cvcert1.getCVCertificate().getCertificateBody().getHolderReference().getConcatenated());
        request = caAdminSession.makeRequest(admin, cvcainfo.getCAId(), cachain, cvcainfo.getCAToken().getAliasFromPurpose(CATokenConstants.CAKEYPURPOSE_CERTSIGN));
        obj = CertificateParser.parseCVCObject(request);
        // We should have created an un-authenticated request, because there
        // does not exist any old key
        CVCertificate cvcertreq = ((CVCAuthenticatedRequest) obj).getRequest();
        assertEquals("SETCVCAEC00000", cvcertreq.getCertificateBody().getHolderReference().getConcatenated());
        assertEquals("SETCVCAEC00000", cvcertreq.getCertificateBody().getAuthorityReference().getConcatenated());

        // Renew the CVCA, generating new keys
        caAdminSession.renewCA(admin, cvcainfo.getCAId(), true, null, true);

        // Make a certificate request from a CVCA again
        cvcainfo = caSession.getCAInfo(admin, TEST_CVC_ECC_CA_NAME);
        cachain = cvcainfo.getCertificateChain();
        assertEquals(1, cachain.size());
        Certificate cert2 = (Certificate) cachain.iterator().next();
        CardVerifiableCertificate cvcert2 = (CardVerifiableCertificate) cert2;
        assertEquals("SETCVCAEC00001", cvcert2.getCVCertificate().getCertificateBody().getHolderReference().getConcatenated());
        request = caAdminSession.makeRequest(admin, cvcainfo.getCAId(), cachain, cvcainfo.getCAToken().getAliasFromPurpose(CATokenConstants.CAKEYPURPOSE_CERTSIGN));
        obj = CertificateParser.parseCVCObject(request);
        // We should have created an authenticated request signed by the old
        // certificate
        CVCAuthenticatedRequest authreq1 = (CVCAuthenticatedRequest) obj;
        CVCertificate reqcert1 = authreq1.getRequest();
        assertEquals("SETCVCAEC00001", reqcert1.getCertificateBody().getHolderReference().getConcatenated());
        assertEquals("SETCVCAEC00001", reqcert1.getCertificateBody().getAuthorityReference().getConcatenated());
        // Cleanup
        removeOldCa("TESTCVCAECC");
        removeOldCa("TESTDVECC-D");
        removeOldCa("TESTDVECC-F");
    } // test10AddCVCCAECC

    /**
     * Test that we can create a SubCA signed by an external RootCA. The SubCA create a certificate request sent to the RootCA that creates a
     * certificate which is then received on the SubCA again.
     * 
     * @throws Exception
     */
    @Test
    public void test11RSASignedByExternal() throws Exception {
        log.trace(">" + Thread.currentThread().getStackTrace()[1].getMethodName() + "()");
        removeOldCa("TESTSIGNEDBYEXTERNAL");

        List<Certificate> toremove = new ArrayList<Certificate>();
        try {
            final CAToken caToken = createCaToken("test11", "1024", AlgorithmConstants.SIGALG_SHA1_WITH_RSA, AlgorithmConstants.SIGALG_SHA1_WITH_RSA);
            // Create and active OSCP CA Service.
            final List<ExtendedCAServiceInfo> extendedcaservices = new ArrayList<ExtendedCAServiceInfo>();
            extendedcaservices.add(new XKMSCAServiceInfo(ExtendedCAServiceInfo.STATUS_INACTIVE, "CN=XKMSCertificate, " + "CN=TESTSIGNEDBYEXTERNAL",
                    "", "1024", AlgorithmConstants.KEYALGORITHM_RSA));
            extendedcaservices.add(new CmsCAServiceInfo(ExtendedCAServiceInfo.STATUS_INACTIVE, "CN=CMSCertificate, " + "CN=TESTSIGNEDBYEXTERNAL", "",
                    "1024", AlgorithmConstants.KEYALGORITHM_RSA));

            X509CAInfo cainfo = new X509CAInfo("CN=TESTSIGNEDBYEXTERNAL", "TESTSIGNEDBYEXTERNAL", CAConstants.CA_ACTIVE, new Date(), "",
                    CertificateProfileConstants.CERTPROFILE_FIXED_SUBCA, 1000, null, // Expiretime
                    CAInfo.CATYPE_X509, CAInfo.SIGNEDBYEXTERNALCA, // Signed by the first TEST CA we created
                    (Collection<Certificate>) null, caToken, "JUnit RSA CA Signed by external", -1, null, null, // PolicyId
                    24 * SimpleTime.MILLISECONDS_PER_HOUR, // CRLPeriod
                    0 * SimpleTime.MILLISECONDS_PER_HOUR, // CRLIssueInterval
                    10 * SimpleTime.MILLISECONDS_PER_HOUR, // CRLOverlapTime
                    10 * SimpleTime.MILLISECONDS_PER_HOUR, // DeltaCRLPeriod
                    new ArrayList<Integer>(), true, // Authority Key Identifier
                    false, // Authority Key Identifier Critical
                    true, // CRL Number
                    false, // CRL Number Critical
                    null, // defaultcrldistpoint
                    null, // defaultcrlissuer
                    null, // defaultocsplocator
                    null, // Authority Information Access
                    null, // defaultfreshestcrl
                    true, // Finish User
                    extendedcaservices, false, // use default utf8 settings
                    new ArrayList<Integer>(), // Approvals Settings
                    1, // Number of Req approvals
                    false, // Use UTF8 subject DN by default
                    true, // Use LDAP DN order by default
                    false, // Use CRL Distribution Point on CRL
                    false, // CRL Distribution Point on CRL critical
                    true, true, // isDoEnforceUniquePublicKeys
                    true, // isDoEnforceUniqueDistinguishedName
                    false, // isDoEnforceUniqueSubjectDNSerialnumber
                    false, // useCertReqHistory
                    true, // useUserStorage
                    true, // useCertificateStorage
                    null // cmpRaAuthSecret
            );

            try {
                caSession.getCAInfo(admin, "TESTSIGNEDBYEXTERNAL");
                fail("External CA exists in database. Test can't continue.");
            } catch (CADoesntExistsException e) {
                // Life is awesome
            }
            caAdminSession.createCA(admin, cainfo);

            CAInfo info = caSession.getCAInfo(admin, "TESTSIGNEDBYEXTERNAL");
            assertEquals("Creating an initial CA signed by external should have the CA in status WAITING_CERTIFICATE_RESPONSE", CAConstants.CA_WAITING_CERTIFICATE_RESPONSE, info.getStatus());

            // Generate a certificate request from the CA and send to the TEST CA
            CAInfo rootinfo = caSession.getCAInfo(caAdmin, getTestCAName());
            Collection<Certificate> rootcacertchain = rootinfo.getCertificateChain();
            byte[] request = caAdminSession.makeRequest(admin, info.getCAId(), rootcacertchain, info.getCAToken().getAliasFromPurpose(CATokenConstants.CAKEYPURPOSE_CERTSIGN));
            info = caSession.getCAInfo(admin, "TESTSIGNEDBYEXTERNAL");
            assertEquals("Making an initial certificate request should have the CA in status WAITING_CERTIFICATE_RESPONSE", CAConstants.CA_WAITING_CERTIFICATE_RESPONSE, info.getStatus());
            PKCS10RequestMessage msg = new PKCS10RequestMessage(request);
            assertEquals("CN=TESTSIGNEDBYEXTERNAL", msg.getRequestDN());

            // Receive the certificate request on the TEST CA
            info.setSignedBy("CN=TEST".hashCode());
            ResponseMessage resp = caAdminSession.processRequest(admin, info, msg);

            // Receive the signed certificate back on our SubCA
            caAdminSession.receiveResponse(admin, info.getCAId(), resp, null, null);
            Collection<Certificate> certs = certificateStoreSession.findCertificatesBySubject(info.getSubjectDN());
            toremove.addAll(certs); // Remove CA certificate after completed test

            // Check that the CA has the correct certificate chain now
            info = caSession.getCAInfo(admin, "TESTSIGNEDBYEXTERNAL");
            assertEquals(CAConstants.CA_ACTIVE, info.getStatus());
            Iterator<Certificate> iter = info.getCertificateChain().iterator();
            Certificate cert = iter.next();
            String sigAlg = AlgorithmTools.getSignatureAlgorithm(cert);
            assertEquals(AlgorithmConstants.SIGALG_SHA1_WITH_RSA, sigAlg);
            assertTrue("Error in created ca certificate", CertTools.getSubjectDN(cert).equals("CN=TESTSIGNEDBYEXTERNAL"));
            assertTrue("Error in created ca certificate", CertTools.getIssuerDN(cert).equals("CN=TEST"));
            assertTrue("Creating CA failed", info.getSubjectDN().equals("CN=TESTSIGNEDBYEXTERNAL"));
            PublicKey pk = cert.getPublicKey();
            if (pk instanceof RSAPublicKey) {
                RSAPublicKey rsapk = (RSAPublicKey) pk;
                assertEquals(rsapk.getAlgorithm(), "RSA");
            } else {
                assertTrue("Public key is not EC", false);
            }
            cert = (X509Certificate) iter.next();
            assertTrue("Error in root ca certificate", CertTools.getSubjectDN(cert).equals("CN=TEST"));
            assertTrue("Error in root ca certificate", CertTools.getIssuerDN(cert).equals("CN=TEST"));

            // Make a new certificate request from the CA
            Collection<Certificate> cachain = info.getCertificateChain();
            request = caAdminSession.makeRequest(admin, info.getCAId(), cachain, info.getCAToken().getAliasFromPurpose(CATokenConstants.CAKEYPURPOSE_CERTSIGN));
            info = caSession.getCAInfo(admin, "TESTSIGNEDBYEXTERNAL");
            assertEquals(CAConstants.CA_ACTIVE, info.getStatus()); // No new keys
            // generated, still active
            msg = new PKCS10RequestMessage(request);
            assertEquals("CN=TESTSIGNEDBYEXTERNAL", msg.getRequestDN());
            
            // Add another certificate for the subCA so we have more than 1 to revoke
            // Generate a certificate request from the CA and send to the TEST CA
            request = caAdminSession.makeRequest(admin, info.getCAId(), rootcacertchain, info.getCAToken().getAliasFromPurpose(CATokenConstants.CAKEYPURPOSE_CERTSIGN));
            info = caSession.getCAInfo(admin, "TESTSIGNEDBYEXTERNAL");
            // CA should still active after only making a new request
            assertEquals("CA should still active after only making a new request", CAConstants.CA_ACTIVE, info.getStatus());
            msg = new PKCS10RequestMessage(request);
            assertEquals("CN=TESTSIGNEDBYEXTERNAL", msg.getRequestDN());
            // Receive the certificate request on the TEST CA
            info.setSignedBy("CN=TEST".hashCode());
            resp = caAdminSession.processRequest(admin, info, msg);
            // Receive the signed certificate back on our SubCA
            caAdminSession.receiveResponse(admin, info.getCAId(), resp, null, null);
            
            // Ensure all issued subCA certificates are cleaned out after test finishes
            certs = certificateStoreSession.findCertificatesBySubject(info.getSubjectDN());
            toremove.addAll(certs); 
            assertEquals("Test CA should have two certificates", 2, certs.size());
            CertificateInfo certinfo = certificateStoreSession.getCertificateInfo(CertTools.getFingerprintAsString(certs.iterator().next()));
            assertEquals("Certificate should have status ACTIVE", CertificateConstants.CERT_ACTIVE, certinfo.getStatus());
            certinfo = certificateStoreSession.getCertificateInfo(CertTools.getFingerprintAsString(certs.iterator().next()));
            assertEquals("Certificate should have status ACTIVE", CertificateConstants.CERT_ACTIVE, certinfo.getStatus());

            // Revoke the subCA, both subCA certificates should be revoked
            caAdminSession.revokeCA(admin, info.getCAId(), RevokedCertInfo.REVOCATION_REASON_CESSATIONOFOPERATION);
            certs = certificateStoreSession.findCertificatesBySubject(info.getSubjectDN());
            assertEquals("Test CA should have two certificates", 2, certs.size());
            iter = certs.iterator();
            final String fp1 = CertTools.getFingerprintAsString(iter.next());
            certinfo = certificateStoreSession.getCertificateInfo(fp1);
            assertEquals("Certificate should have status REVOKED", CertificateConstants.CERT_REVOKED, certinfo.getStatus());
            assertEquals("Revocation reason should be CESSATIONOFOPERATION", RevokedCertInfo.REVOCATION_REASON_CESSATIONOFOPERATION, certinfo.getRevocationReason());
            final String fp2 = CertTools.getFingerprintAsString(iter.next());
            assertFalse(fp1.equals(fp2));
            certinfo = certificateStoreSession.getCertificateInfo(fp2);
            assertEquals("Certificate should have status REVOKED", CertificateConstants.CERT_REVOKED, certinfo.getStatus());            
            assertEquals("Revocation reason should be CESSATIONOFOPERATION", RevokedCertInfo.REVOCATION_REASON_CESSATIONOFOPERATION, certinfo.getRevocationReason());
            
        } catch (CAExistsException pee) {
            log.info("CA exists: ", pee);
        } finally {
            removeOldCa("TESTSIGNEDBYEXTERNAL");            
            // Remove the test certificates from the database
            for (Certificate certificate : toremove) {
                internalCertStoreSession.removeCertificate(CertTools.getFingerprintAsString(certificate));                
            }
        }

    } // test10RSASignedByExternal

    /**
     * adds a CA using DSA keys to the database.
     * 
     * It also checks that the CA is stored correctly.
     * 
     * @throws Exception error
     */
    @Test
    public void test12AddDSACA() throws Exception {
        log.trace(">" + Thread.currentThread().getStackTrace()[1].getMethodName() + "()");
        boolean ret = false;

        removeTestCA(TEST_DSA_CA_NAME); // We cant be sure this CA was not left
        createDefaultDsaCa();
        CAInfo info = caSession.getCAInfo(admin, TEST_DSA_CA_NAME);

        Collection<Certificate> rootcacertchain = info.getCertificateChain();
        X509Certificate cert = (X509Certificate) rootcacertchain.iterator().next();
        String sigAlg = AlgorithmTools.getSignatureAlgorithm(cert);
        assertEquals(AlgorithmConstants.SIGALG_SHA1_WITH_DSA, sigAlg);
        assertTrue("Error in created ca certificate", cert.getSubjectDN().toString().equals("CN=TESTDSA"));
        assertTrue("Creating CA failed", info.getSubjectDN().equals("CN=TESTDSA"));
        PublicKey pk = cert.getPublicKey();
        if (pk instanceof DSAPublicKey) {
            DSAPublicKey rsapk = (DSAPublicKey) pk;
            assertEquals(rsapk.getAlgorithm(), "DSA");
        } else {
            assertTrue("Public key is not DSA", false);
        }
        assertTrue(
                "CA is not valid for the specified duration.",
                cert.getNotAfter().after(new Date(new Date().getTime() + 10 * 364 * 24 * 60 * 60 * 1000L))
                        && cert.getNotAfter().before(new Date(new Date().getTime() + 10 * 366 * 24 * 60 * 60 * 1000L)));
        ret = true;

        // Test to generate a certificate request from the CA
        Collection<Certificate> cachain = info.getCertificateChain();
        byte[] request = caAdminSession.makeRequest(admin, info.getCAId(), cachain, info.getCAToken().getAliasFromPurpose(CATokenConstants.CAKEYPURPOSE_CERTSIGN));
        PKCS10RequestMessage msg = new PKCS10RequestMessage(request);
        assertEquals("CN=TESTDSA", msg.getRequestDN());

        assertTrue("Creating DSA CA failed", ret);
        removeOldCa(TEST_DSA_CA_NAME);
    } // test12AddDSACA

    @Test
    public void test13RenewCA() throws Exception {
        // Test renew cacert
        CAInfo info = caSession.getCAInfo(admin, getTestCAId());
        Date oldExpire = info.getExpireTime();
        Collection<Certificate> certs = info.getCertificateChain();
        X509Certificate cacert1 = (X509Certificate) certs.iterator().next();
        Thread.sleep(1000); // Sleep 1 second so new validity does not have a chance to be the same as old
        // Use a certificate profile with a policy ID when renewing the CA
        final String caProfileName = "TEST_CA_PROFILE_WITH_POLICY";
        try {
            // Set a policy in the CA settings, and check for values
            X509CAInfo xinfo = (X509CAInfo)info;
            List<CertificatePolicy> policies = new ArrayList<CertificatePolicy>();
            CertificatePolicy pol = new CertificatePolicy("2.2.2.2", null, null);
            policies.add(pol);
            xinfo.setPolicies(policies);
            caSession.editCA(admin, xinfo);
            caAdminSession.renewCA(admin, getTestCAId(), false, null, false);
            info = caSession.getCAInfo(admin, getTestCAId());
            certs = info.getCertificateChain();
            X509Certificate cacert2 = (X509Certificate) certs.iterator().next();
            // cacert2 should have the policy defined in the CA settings only
            String policyId = CertTools.getCertificatePolicyId(cacert2, 0);
            assertNotNull("CA certificate after renewal should have a Certificate Policy from CA settings", policyId);
            assertEquals("2.2.2.2", policyId);
            policyId = CertTools.getCertificatePolicyId(cacert2, 1);
            assertNull(policyId);
            assertFalse(cacert1.getSerialNumber().equals(cacert2.getSerialNumber()));
            assertEquals(new String(CertTools.getSubjectKeyId(cacert1)), new String(CertTools.getSubjectKeyId(cacert2)));
            cacert2.verify(cacert1.getPublicKey()); // throws if it fails
            assertTrue("Renewed CA expire time should be after old one: "+info.getExpireTime()+", old: "+oldExpire, oldExpire.before(info.getExpireTime()));
            // Add a new policy to the certificate profile, and renew again to verify certificate policy handling during renewal
            final CertificateProfile caProfile = new CertificateProfile(CertificateProfileConstants.CERTPROFILE_FIXED_ROOTCA);
            policies = new ArrayList<CertificatePolicy>();
            pol = new CertificatePolicy("1.1.1.1", null, null);
            policies.add(pol);
            caProfile.setCertificatePolicies(policies);
            final int certificateprofileid = certificateProfileSession.addCertificateProfile(admin, caProfileName, caProfile);
            info.setCertificateProfileId(certificateprofileid);
            caAdminSession.editCA(admin, info);
            // Now we have edited the CA with the new Certificate Profile, let's renew it
            caAdminSession.renewCA(admin, getTestCAId(), false, null, false);
            info = caSession.getCAInfo(admin, getTestCAId());
            certs = info.getCertificateChain();
            X509Certificate cacert3 = (X509Certificate) certs.iterator().next();
            policyId = CertTools.getCertificatePolicyId(cacert3, 0);
            assertNotNull("CA certficiate after renewal should also have Certificate Policy from certificate profile", policyId);
            assertEquals("1.1.1.1", policyId);
            policyId = CertTools.getCertificatePolicyId(cacert3, 1);
            assertNotNull("CA certficiate after renewal should have Certificate Policy from CA settings", policyId);
            assertEquals("2.2.2.2", policyId);

            // Test renew CA keys
            caAdminSession.renewCA(admin, getTestCAId(), true, null, true);
            info = caSession.getCAInfo(admin, getTestCAId());
            certs = info.getCertificateChain();
            X509Certificate cacert4 = (X509Certificate) certs.iterator().next();
            assertFalse(cacert2.getSerialNumber().equals(cacert4.getSerialNumber()));
            String keyid1 = new String(CertTools.getSubjectKeyId(cacert3));
            String keyid2 = new String(CertTools.getSubjectKeyId(cacert4));
            assertFalse(keyid1.equals(keyid2));

            // Test create X.509 link certificate (NewWithOld rollover cert)
            // We have cacert3 that we want to sign with the old keys from cacert2,
            // create a link certificate.
            // That link certificate should have the same subjetcKeyId as cert3, but
            // be possible to verify with cert2.
            byte[] bytes = caAdminSession.getLatestLinkCertificate(getTestCAId());
            X509Certificate cacert5 = (X509Certificate) CertTools.getCertfromByteArray(bytes);
            // Same public key as in cacert3 -> same subject key id
            keyid1 = new String(CertTools.getSubjectKeyId(cacert4));
            keyid2 = new String(CertTools.getSubjectKeyId(cacert5));
            assertTrue(keyid1.equals(keyid2));
            // Same signer as for cacert2 -> same auth key id in cacert4 as subject
            // key id in cacert2
            keyid1 = new String(CertTools.getSubjectKeyId(cacert3));
            keyid2 = new String(CertTools.getAuthorityKeyId(cacert5));
            assertTrue(keyid1.equals(keyid2));
            cacert5.verify(cacert2.getPublicKey());

            // Test make request just making a request using the old keys
            byte[] request = caAdminSession.makeRequest(admin, getTestCAId(), new ArrayList<Certificate>(), info.getCAToken().getAliasFromPurpose(CATokenConstants.CAKEYPURPOSE_CERTSIGN));
            assertNotNull(request);
            PKCS10RequestMessage msg = RequestMessageUtils.genPKCS10RequestMessage(request);
            PublicKey pk1 = cacert4.getPublicKey();
            PublicKey pk2 = msg.getRequestPublicKey();
            String key1 = new String(Base64.encode(pk1.getEncoded()));
            String key2 = new String(Base64.encode(pk2.getEncoded()));
            // A plain request using the CAs key will have the same public key
            assertEquals(key1, key2);
            // Test make request generating new keys
            request = caAdminSession.makeRequest(admin, getTestCAId(), new ArrayList<Certificate>(), null);
            assertNotNull(request);
            msg = RequestMessageUtils.genPKCS10RequestMessage(request);
            pk1 = cacert4.getPublicKey();
            pk2 = msg.getRequestPublicKey();
            key1 = new String(Base64.encode(pk1.getEncoded()));
            key2 = new String(Base64.encode(pk2.getEncoded()));
            // A plain request using new CAs key can not have the same keys
            assertFalse(key1.equals(key2));
            /*
             * After this (new keys activated but no cert response received) status should still be ACTIVE
             * since we have an valid CA certificate.
             * (CAConstants.CA_WAITING_CERTIFICATE_RESPONSE is only when we have no cert yet, like for an
             * initial externally signed subCA.)
             */
            info = caSession.getCAInfo(admin, getTestCAId());
            assertEquals(CAConstants.CA_ACTIVE, info.getStatus());

            // To clean up after us so the active key is not out of sync with the
            // active certificate, we should simply renew the CA
            info.setStatus(CAConstants.CA_ACTIVE);
            caAdminSession.editCA(admin, info); // need active status in order
            // finally do a new renew
            caAdminSession.renewCA(admin, getTestCAId(), false, null, false);
        } finally {
            certificateProfileSession.removeCertificateProfile(admin, caProfileName);
        }
    } // test13RenewCA

    @Test
    public void test14RevokeCA() throws Exception {
        log.trace(">" + Thread.currentThread().getStackTrace()[1].getMethodName() + "()");
        final String caname = "TestRevokeCA";
        removeTestCA(caname);
        createTestCA(caname);
        List<Certificate> toremove = new ArrayList<Certificate>();
        try {
            CAInfo info = caSession.getCAInfo(admin, caname);
            assertEquals(CAConstants.CA_ACTIVE, info.getStatus());
            assertEquals(RevokedCertInfo.NOT_REVOKED, info.getRevocationReason());
            assertNull(info.getRevocationDate());

            // Revoke the CA
            caAdminSession.revokeCA(admin, info.getCAId(), RevokedCertInfo.REVOCATION_REASON_CACOMPROMISE);
            info = caSession.getCAInfo(admin, caname);
            assertEquals(CAConstants.CA_REVOKED, info.getStatus());
            assertEquals(RevokedCertInfo.REVOCATION_REASON_CACOMPROMISE, info.getRevocationReason());
            assertTrue(info.getRevocationDate().getTime() > 0);
            // Check that the CAs certificate(s) are revoked.
            Collection<Certificate> certs = certificateStoreSession.findCertificatesBySubject(info.getSubjectDN());
            toremove.addAll(certs);
            assertEquals("Test CA should have one certificate", 1, certs.size());
            final String fp = CertTools.getFingerprintAsString(certs.iterator().next());
            CertificateInfo certinfo = certificateStoreSession.getCertificateInfo(fp);
            assertEquals("Certificate should have status REVOKED", CertificateConstants.CERT_REVOKED, certinfo.getStatus());
            // Renew the CA, twice, we have to "unrevoke it first though
            info.setStatus(CAConstants.CA_ACTIVE);
            caSession.editCA(admin, info);
            caAdminSession.renewCA(admin, info.getCAId(), false, null, false);
            caAdminSession.renewCA(admin, info.getCAId(), false, null, false);
            certs = certificateStoreSession.findCertificatesBySubject(info.getSubjectDN());
            toremove.addAll(certs);
            assertEquals("Test CA should have three certificates", 3, certs.size());
            // Remove the old revoked one one to make it easier for us
            internalCertStoreSession.removeCertificate(fp);                
            certs = certificateStoreSession.findCertificatesBySubject(info.getSubjectDN());
            assertEquals("Test CA should have two certificates", 2, certs.size());
            Iterator<Certificate> iter = certs.iterator();
            certinfo = certificateStoreSession.getCertificateInfo(CertTools.getFingerprintAsString(iter.next()));
            assertEquals("Certificate should have status ACTIVE", CertificateConstants.CERT_ACTIVE, certinfo.getStatus());
            certinfo = certificateStoreSession.getCertificateInfo(CertTools.getFingerprintAsString(iter.next()));
            assertEquals("Certificate should have status ACTIVE", CertificateConstants.CERT_ACTIVE, certinfo.getStatus());
            // Revoke the CA with two active certificates, both should be revoked
            caAdminSession.revokeCA(admin, info.getCAId(), RevokedCertInfo.REVOCATION_REASON_CESSATIONOFOPERATION);
            certs = certificateStoreSession.findCertificatesBySubject(info.getSubjectDN());
            assertEquals("Test CA should have two certificates", 2, certs.size());
            iter = certs.iterator();
            final String fp1 = CertTools.getFingerprintAsString(iter.next());
            certinfo = certificateStoreSession.getCertificateInfo(fp1);
            assertEquals("Certificate should have status REVOKED", CertificateConstants.CERT_REVOKED, certinfo.getStatus());
            assertEquals("Revocation reason should be CESSATIONOFOPERATION", RevokedCertInfo.REVOCATION_REASON_CESSATIONOFOPERATION, certinfo.getRevocationReason());
            final String fp2 = CertTools.getFingerprintAsString(iter.next());
            assertFalse(fp1.equals(fp2));
            certinfo = certificateStoreSession.getCertificateInfo(fp2);
            assertEquals("Certificate should have status REVOKED", CertificateConstants.CERT_REVOKED, certinfo.getStatus());            
            assertEquals("Revocation reason should be CESSATIONOFOPERATION", RevokedCertInfo.REVOCATION_REASON_CESSATIONOFOPERATION, certinfo.getRevocationReason());
        } finally {
            // Remove the test CA
            removeTestCA(caname);
            // Remove the test CAs certificates from the database
            for (Certificate certificate : toremove) {
                internalCertStoreSession.removeCertificate(CertTools.getFingerprintAsString(certificate));                
            }
        }
    } // test14RevokeCA

    @Test
    public void test15ExternalExpiredCA() throws Exception {
        log.trace(">" + Thread.currentThread().getStackTrace()[1].getMethodName() + "()");
        final String caname = "TestExternalExpiredCA";
        byte[] testcert = Base64.decode(("MIICDjCCAXegAwIBAgIIaXCEunuPDowwDQYJKoZIhvcNAQEFBQAwFzEVMBMGA1UE"
                + "AwwMc2hvcnQgZXhwaXJlMB4XDTExMDIwNTE3MjI1MloXDTExMDIwNTE4MjIxM1ow"
                + "FzEVMBMGA1UEAwwMc2hvcnQgZXhwaXJlMIGfMA0GCSqGSIb3DQEBAQUAA4GNADCB"
                + "iQKBgQCNAygw3H9WuThxxFAv2oc5SzijHLUdvgD+Y9E3nKWWgRq1ECcKo0d60U24"
                + "gJiuSkH+PcC300a1AnfWAac/MkuFS9F58J6vjud+AA0MzoD5Tlc9lbxQy6qoKF29"
                + "87VMITZjISSdfnlfWbXVeNqTrqeTBreOS34TTZ7bLzBCvGcq1wIDAQABo2MwYTAd"
                + "BgNVHQ4EFgQUfLHTt9G8cdsVxZR9gOsHUqqh/1wwDwYDVR0TAQH/BAUwAwEB/zAf"
                + "BgNVHSMEGDAWgBR8sdO30bxx2xXFlH2A6wdSqqH/XDAOBgNVHQ8BAf8EBAMCAYYw"
                + "DQYJKoZIhvcNAQEFBQADgYEAS4PvelI9Fmxxcbs0Nrx8qk+TlREOeDX+rsXvKcJ2"
                + "gGEhtMX1yCNn0uSQuc/mM4Dz5faxCCQQMZl8Vp07d1MrTMYcka+P6RtEKneXfLim"
                + "fXnqR22xd2P7ssXE52/tTnAyJbYUrOOCI6iiek3dZN8oTmGhZUBHIgFzxC/8MgHa" + "G6Y=").getBytes());
        Certificate cert = CertTools.getCertfromByteArray(testcert);
        removeOldCa(caname); // for the test
        List<Certificate> certs = new ArrayList<Certificate>();
        certs.add(cert);

        try {
            // Import the CA certificate
            caAdminSession.importCACertificate(admin, caname, certs);
            CAInfo info = caSession.getCAInfo(admin, caname);
            // The CA must not get stats SecConst.CA_EXPIRED when it is an external CA
            assertEquals(CAConstants.CA_EXTERNAL, info.getStatus());
        } finally {
            removeOldCa(caname); // for the test
        }
    } // test15ExternalExpiredCA

    /** Try to create a CA using invalid parameters */
    @Test
    public void test16InvalidCreateCaActions() throws Exception {
        log.trace(">test16InvalidCreateCaActions()");
        // We cant be sure this CA was not left over from some other failed test
        removeTestCA("TESTFAIL");
        final CAToken caToken = createCaToken("test16", "1024", AlgorithmConstants.SIGALG_SHA1_WITH_RSA, AlgorithmConstants.SIGALG_SHA1_WITH_RSA);
        // Create and active OSCP CA Service.
        final List<ExtendedCAServiceInfo> extendedcaservices = new ArrayList<ExtendedCAServiceInfo>();

        extendedcaservices.add(new XKMSCAServiceInfo(ExtendedCAServiceInfo.STATUS_INACTIVE, "CN=XKMSCertificate, " + "CN=TEST", "", "1024",
                AlgorithmConstants.KEYALGORITHM_RSA));

        X509CAInfo cainfo = new X509CAInfo("CN=TESTFAIL", "TESTFAIL", CAConstants.CA_ACTIVE, new Date(), "", CertificateProfileConstants.CERTPROFILE_FIXED_ROOTCA, 3650,
                null, // Expiretime
                CAInfo.CATYPE_X509, CAInfo.SELFSIGNED, (Collection<Certificate>) null, caToken, "JUnit RSA CA", -1, null, null, // PolicyId
                24 * SimpleTime.MILLISECONDS_PER_HOUR, // CRLPeriod
                0 * SimpleTime.MILLISECONDS_PER_HOUR, // CRLIssueInterval
                10 * SimpleTime.MILLISECONDS_PER_HOUR, // CRLOverlapTime
                10 * SimpleTime.MILLISECONDS_PER_HOUR, // DeltaCRLPeriod
                new ArrayList<Integer>(), true, // Authority Key Identifier
                false, // Authority Key Identifier Critical
                true, // CRL Number
                false, // CRL Number Critical
                null, // defaultcrldistpoint
                null, // defaultcrlissuer
                null, // defaultocsplocator
                null, // Authority Information Access
                null, // defaultfreshestcrl
                true, // Finish User
                extendedcaservices, false, // use default utf8 settings
                new ArrayList<Integer>(), // Approvals Settings
                1, // Number of Req approvals
                false, // Use UTF8 subject DN by default
                true, // Use LDAP DN order by default
                false, // Use CRL Distribution Point on CRL
                false, // CRL Distribution Point on CRL critical
                true, true, // isDoEnforceUniquePublicKeys
                true, // isDoEnforceUniqueDistinguishedName
                false, // isDoEnforceUniqueSubjectDNSerialnumber
                false, // useCertReqHistory
                true, // useUserStorage
                true, // useCertificateStorage
                null // cmpRaAuthSecret
        );

        Set<Principal> principals = new HashSet<Principal>();
        principals.add(new X500Principal("C=SE,O=UnlovedUser,CN=UnlovedUser"));
        AuthenticationToken unpriviledgedUser = simpleAuthenticationProvider.authenticate(new AuthenticationSubject(principals, null));
        // Try to create the CA as an unprivileged user
        try {
            caAdminSession.createCA(unpriviledgedUser, cainfo);
            assertTrue("Was able to create CA as unprivileged user.", false);
        } catch (AuthorizationDeniedException e) {
            // Expected
        }
        // Try to create the CA with a 0 >= CA Id < CAInfo.SPECIALCAIDBORDER
        setPrivateFieldInSuper(cainfo, "caid", CAInfo.SPECIALCAIDBORDER - 1);
        try {
            caAdminSession.createCA(admin, cainfo);
            assertTrue("Was able to create CA with reserved CA Id.", false);
        } catch (CAExistsException e) {
            // Expected
        }
        // Try to create a CA where the CA Id already exists (but not the name)
        CAInfo caInfoTest = caSession.getCAInfo(admin, getTestCAName());
        setPrivateFieldInSuper(cainfo, "caid", caInfoTest.getCAId());
        try {
            caAdminSession.createCA(admin, cainfo);
            assertTrue("Was able to create CA with CA Id of already existing CA.", false);
        } catch (CAExistsException e) {
            // Expected
        }
        CryptoTokenManagementSessionTest.removeCryptoToken(admin, caToken.getCryptoTokenId());
        log.trace("<test16InvalidCreateCaActions()");
    }

    @Test
    public void test17InvalidEditCaActions() throws Exception {
        log.trace(">test17InvalidEditCaActions()");
        CAInfo caInfoTest = caSession.getCAInfo(admin, getTestCAName());
        // Try to edit the CA as an unprivileged user

        Set<Principal> principals = new HashSet<Principal>();
        principals.add(new X500Principal("C=SE,O=UnlovedUser,CN=UnlovedUser"));
        AuthenticationToken unpriviledgedUser = simpleAuthenticationProvider.authenticate(new AuthenticationSubject(principals, null));

        try {
            caAdminSession.editCA(unpriviledgedUser, caInfoTest);
            assertTrue("Was able to edit CA as unprivileged user.", false);
        } catch (AuthorizationDeniedException e) {
            // Expected
        }
        log.trace("<test17InvalidEditCaActions()");
    }

    /** Get CA Info using an unprivileged admin and then trying by pretending to be privileged. */
    @Test
    public void test18PublicWebCaInfoFetch() throws Exception {
        log.trace(">test18PublicWebCaInfoFetch()");

        Set<Principal> principals = new HashSet<Principal>();
        principals.add(new X500Principal("C=SE,O=UnlovedUser,CN=UnlovedUser"));
        AuthenticationToken unpriviledgedUser = simpleAuthenticationProvider.authenticate(new AuthenticationSubject(principals, null));

        // Try to get CAInfo as an unprivileged user using remote EJB
        try {
            caSession.getCAInfo(unpriviledgedUser, getTestCAName());
            fail("Was able to get CA info from remote EJB/CLI pretending to be an unpriviledged user");
        } catch (AuthorizationDeniedException ignored) {
            // OK
        }
        try {
            caSession.getCAInfo(unpriviledgedUser, "CN=TEST".hashCode());
            fail("Was able to get CA info from remote EJB/CLI pretending to be an unpriviledged user");
        } catch (AuthorizationDeniedException ignored) {
            // OK
        }

        log.trace("<test18PublicWebCaInfoFetch()");
    }

    @Test
    public void test19UnprivilegedCaMakeRequest() throws Exception {
        log.trace(">test19UnprivilegedCaMakeRequest()");
        Set<Principal> principals = new HashSet<Principal>();
        principals.add(new X500Principal("C=SE,O=UnlovedUser,CN=UnlovedUser"));
        AuthenticationToken unpriviledgedUser = simpleAuthenticationProvider.authenticate(new AuthenticationSubject(principals, null));
        try {
            caAdminSession.makeRequest(unpriviledgedUser, 0, null, null);
            assertTrue("Was able to make request to CA as unprivileged user.", false);
        } catch (AuthorizationDeniedException e) {
            // Expected
        }
        log.trace("<test19UnprivilegedCaMakeRequest()");
    }

    @Test
    public void test20BadCaReceiveResponse() throws Exception {
        log.trace(">" + Thread.currentThread().getStackTrace()[1].getMethodName() + "()");
        Set<Principal> principals = new HashSet<Principal>();
        principals.add(new X500Principal("C=SE,O=UnlovedUser,CN=UnlovedUser"));
        AuthenticationToken unpriviledgedUser = simpleAuthenticationProvider.authenticate(new AuthenticationSubject(principals, null));

        log.trace(">test20BadCaReceiveResponse()");
        try {
            caAdminSession.receiveResponse(unpriviledgedUser, "CN=TEST".hashCode(), null, null, null);
            fail("Was able to receiveResponse for a CA as unprivileged user.");
        } catch (AuthorizationDeniedException e) {
            // Expected
        }
        try {
            caAdminSession.receiveResponse(admin, -1, null, null, null);
            fail("Was able to receiveResponse for a CA that does not exist.");
        } catch (CADoesntExistsException e) {
            // Expected
        }
        try {
            caAdminSession.receiveResponse(admin, "CN=TEST".hashCode(), new CmpResponseMessage(), null, null);
            fail("Was able to receiveResponse for a CA with a non X509ResponseMessage.");
        } catch (EjbcaException e) {
            // Expected
        }
        try {
            CertificateResponseMessage resp = new X509ResponseMessage();
            resp.setCertificate(caSession.getCAInfo(admin, getTestCAName()).getCertificateChain().iterator().next());
            caAdminSession.receiveResponse(admin, "CN=TEST".hashCode(), resp, null, null);
            fail("Was able to receiveResponse for a CA that is not 'signed by external' using wrong chain.");
        } catch (CertPathValidatorException e) {
            // Expected
        }
        log.trace("<test20BadCaReceiveResponse()");
    }

    @Test
    public void test21UnprivilegedCaProcessRequest() throws Exception {
        log.trace(">test21UnprivilegedCaProcessRequest()");
        Set<Principal> principals = new HashSet<Principal>();
        principals.add(new X500Principal("C=SE,O=UnlovedUser,CN=UnlovedUser"));
        AuthenticationToken unpriviledgedUser = simpleAuthenticationProvider.authenticate(new AuthenticationSubject(principals, null));
        CAInfo caInfo = caSession.getCAInfo(admin, getTestCAName());
        try {
            // Try to process a request for a CA with an unprivileged user.
            caAdminSession.processRequest(unpriviledgedUser, caInfo, null);
            fail("Was able to process request to CA as unprivileged user.");
        } catch (AuthorizationDeniedException e) {
            // Expected
        }
        // Try to process a request for a CA with a 0 >= CA Id < CAInfo.SPECIALCAIDBORDER
        setPrivateFieldInSuper(caInfo, "caid", CAInfo.SPECIALCAIDBORDER - 1);
        try {
            caAdminSession.processRequest(admin, caInfo, null);
            fail("Was able to create CA with reserved CA Id.");
        } catch (CAExistsException e) {
            // Expected
        }
        log.trace("<test21UnprivilegedCaProcessRequest()");
    }

    /** Test that we can not create CAs with too short key lengths.
     * CA creation dwith too short keys should result in an InvalidKeyException (wrapped in EJBException of course).
    */
   @Test
   public void test22IllegalKeyLengths() throws Exception {
       log.trace(">" + Thread.currentThread().getStackTrace()[1].getMethodName() + "()");
       // TODO: These tests should be moved to another class and are just here because they replaces older tests with similar purpose
       final Properties cryptoTokenProperties = new Properties();
       cryptoTokenProperties.setProperty(CryptoToken.AUTOACTIVATE_PIN_PROPERTY, "foo1234");
       int cryptoTokenId = 0;
       try {
           cryptoTokenId = cryptoTokenManagementSession.createCryptoToken(admin, this.getClass().getSimpleName() + "." + "test22.IllegalKeyLengthRSA", SoftCryptoToken.class.getName(), cryptoTokenProperties, null, null);
           cryptoTokenManagementSession.createKeyPair(admin, cryptoTokenId, CAToken.SOFTPRIVATESIGNKEYALIAS, "512");
           fail("It should not be possoble to create a keys with 512 bit RSA keys.");
       } catch (Exception e) {
           cryptoTokenManagementSession.deleteCryptoToken(admin, cryptoTokenId);
       }
       try {
           cryptoTokenId = cryptoTokenManagementSession.createCryptoToken(admin, this.getClass().getSimpleName() + "." + "test22.IllegalKeyLengthDSA", SoftCryptoToken.class.getName(), cryptoTokenProperties, null, null);
           cryptoTokenManagementSession.createKeyPair(admin, cryptoTokenId, CAToken.SOFTPRIVATESIGNKEYALIAS, "DSA512");
           fail("It should not be possoble to create a keys with 512 bit DSA keys.");
       } catch (Exception e) {
           cryptoTokenManagementSession.deleteCryptoToken(admin, cryptoTokenId);
       }
       try {
           cryptoTokenId = cryptoTokenManagementSession.createCryptoToken(admin, this.getClass().getSimpleName() + "." + "test22.IllegalKeyLengthECDSA", SoftCryptoToken.class.getName(), cryptoTokenProperties, null, null);
           cryptoTokenManagementSession.createKeyPair(admin, cryptoTokenId, CAToken.SOFTPRIVATESIGNKEYALIAS, "prime192v1");
           fail("It should not be possoble to create a CA with 192 bit ECC keys.");
       } catch (Exception e) {
           cryptoTokenManagementSession.deleteCryptoToken(admin, cryptoTokenId);
       }
    }

   @Test
   public void test23GetAuthorizedPublisherIdsAccessTest() throws Exception {
       log.trace(">test23GetAuthorizedPublisherIdsAccessTest()");
       Set<Principal> principals = new HashSet<Principal>();
       principals.add(new X500Principal("C=SE,O=UnlovedUser,CN=UnlovedUser"));
       AuthenticationToken unpriviledgedUser = simpleAuthenticationProvider.authenticate(new AuthenticationSubject(principals, null));
       final String publisherName = CAsTest.class.getSimpleName();
       try {
           // Create at least one publisher so we know we have something
           final ValidationAuthorityPublisher publ = new ValidationAuthorityPublisher();
           publ.setDataSource("foo");
           publ.setDescription("foobar");
           publisherProxySession.addPublisher(admin, publisherName, publ);
           // Superadmin should get several IDs
           Collection<Integer> caids = caAdminSession.getAuthorizedPublisherIds(admin);
           assertNotNull(caids);
           assertFalse("Superadmin should read some publisher IDs", caids.isEmpty());
           // Unprivilegeduser should get no IDs
           caids = caAdminSession.getAuthorizedPublisherIds(unpriviledgedUser);
           assertTrue("Unprivileged admin should not be allowed to read and publisher IDs", caids.isEmpty());
       } finally {
           publisherProxySession.removePublisher(admin, publisherName);
       }
       log.trace("<test23GetAuthorizedPublisherIdsAccessTest()");
   }

    /**
     * Preemtively remove CA in case it was created by a previous run:
     * 
     * @throws AuthorizationDeniedException
     * @throws CADoesntExistsException 
     */
    private void removeOldCa(String caName) throws AuthorizationDeniedException {
        try {
            final CAInfo info = caSession.getCAInfo(admin, caName);
            try {
                cryptoTokenManagementSession.deleteCryptoToken(admin, info.getCAToken().getCryptoTokenId());
            } catch (Exception e) {
                // Ignore
            }
            caSession.removeCA(admin, info.getCAId());
        } catch (CADoesntExistsException e) {
            // NOPMD: we ignore this
        }
    }

    /** Used for direct manipulation of objects without setters. */
    private void setPrivateFieldInSuper(Object object, String fieldName, Object value) {
        log.trace(">setPrivateField");
        try {
            Field field = object.getClass().getSuperclass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(object, value);
        } catch (Exception e) {
            log.error("", e);
            assertTrue("Could not set " + fieldName + " to " + value + ": " + e.getMessage(), false);
        }
        log.trace("<setPrivateField");
    }

}
