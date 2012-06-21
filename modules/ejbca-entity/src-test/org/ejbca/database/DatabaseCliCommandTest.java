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
package org.ejbca.database;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.beans.XMLDecoder;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Properties;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Query;

import org.cesecore.certificates.ca.CAConstants;
import org.cesecore.certificates.ca.CAData;
import org.cesecore.certificates.ca.CAInfo;
import org.cesecore.certificates.ca.X509CA;
import org.cesecore.certificates.ca.X509CAInfo;
import org.cesecore.certificates.ca.catoken.CAToken;
import org.cesecore.certificates.ca.catoken.CATokenConstants;
import org.cesecore.certificates.ca.catoken.CATokenInfo;
import org.cesecore.certificates.ca.extendedservices.ExtendedCAServiceInfo;
import org.cesecore.certificates.certificateprofile.CertificateProfile;
import org.cesecore.certificates.certificateprofile.CertificateProfileConstants;
import org.cesecore.certificates.certificateprofile.CertificateProfileData;
import org.cesecore.certificates.util.AlgorithmConstants;
import org.cesecore.keys.token.CryptoToken;
import org.cesecore.keys.token.CryptoTokenFactory;
import org.cesecore.keys.token.SoftCryptoToken;
import org.cesecore.util.CertTools;
import org.cesecore.util.CryptoProviderTools;
import org.cesecore.util.StringTools;
import org.easymock.EasyMock;
import org.ejbca.ui.cli.ErrorAdminCommandException;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Unit tests for the class DatabaseCliCommand
 * 
 * @version $Id$
 *
 */
public class DatabaseCliCommandTest {

    private static final String PERSISTENCE_UNIT = "foo";
    
    private DatabaseCliCommandStub command;

    @BeforeClass
    public static void beforeClass() {
        CryptoProviderTools.installBCProviderIfNotAvailable();
    }
    
    @Before
    public void setUp() {
        command = new DatabaseCliCommandStub();         
    }

    @Test
    public void testExportTableBinary() throws ErrorAdminCommandException, IOException, SecurityException, NoSuchFieldException,
            IllegalArgumentException, IllegalAccessException, NoSuchMethodException, InvocationTargetException {
        CertificateProfile certificateProfile = new CertificateProfile(CertificateProfileConstants.CERTPROFILE_FIXED_ENDUSER);
        CertificateProfileData certificateProfileData = new CertificateProfileData(0, PERSISTENCE_UNIT, certificateProfile);
        File exportFile = File.createTempFile("tmp", ".bin");
        long freespaceBefore = exportFile.getFreeSpace();
        performExport(certificateProfileData, exportFile, PERSISTENCE_UNIT, OutputFormat.BINARY);
        long freespaceAfter = exportFile.getFreeSpace();
        assertTrue("Nothing was written to exportfile", freespaceAfter < freespaceBefore);
        
        //Import the binary object again using the same technique as the Command
        final ObjectInputStream ois = new ObjectInputStream(new FileInputStream(exportFile));
        CertificateProfileData result = (CertificateProfileData) command.getNextBatch(ois).get(0);
        assertEquals(certificateProfileData.getCertificateProfileName(), result.getCertificateProfileName());      
    }
    
    @Test
    public void testExportTableXmlWithCertificateProfile() throws IOException, SecurityException, IllegalArgumentException, NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        CertificateProfile certificateProfile = new CertificateProfile(CertificateProfileConstants.CERTPROFILE_FIXED_ENDUSER);
        CertificateProfileData certificateProfileData = new CertificateProfileData(0, PERSISTENCE_UNIT, certificateProfile);
        File exportFile = File.createTempFile("tmp", ".xml");
        long freespaceBefore = exportFile.getFreeSpace();
        performExport(certificateProfileData, exportFile, PERSISTENCE_UNIT, OutputFormat.XML);
        long freespaceAfter = exportFile.getFreeSpace();
        assertTrue("Nothing was written to exportfile", freespaceAfter < freespaceBefore);
        //Perform decoding
        XMLDecoder decoder = new XMLDecoder(new FileInputStream(exportFile));
        CertificateProfileData result = (CertificateProfileData) decoder.readObject();
         assertEquals(certificateProfileData.getCertificateProfileName(), result.getCertificateProfileName()); 
    }
    
    @Test
    public void testExportTableXmlWithCaData() throws Exception {     
        CAData caData = new CAData("CN=foo", "foo", 0, createTestCA("CN=foo", AlgorithmConstants.SIGALG_SHA256_WITH_RSA));
        File exportFile = File.createTempFile("tmp", ".xml");
        long freespaceBefore = exportFile.getFreeSpace();
        performExport(caData, exportFile, PERSISTENCE_UNIT, OutputFormat.XML);
        long freespaceAfter = exportFile.getFreeSpace();
        assertTrue("Nothing was written to exportfile", freespaceAfter < freespaceBefore);
        XMLDecoder decoder = new XMLDecoder(new FileInputStream(exportFile));
        CAData result = (CAData) decoder.readObject();
        assertEquals(caData.getCA().getCAInfo(), result.getCA().getCAInfo());
    }
    
    /**
     * Tests the private method GetNextBatch for XML serialized objects, in 
     * particular multiple CADatas. 
     * @throws Exception 
     */
    @Test
    public void testGetNextBatchWithXml() throws Exception {
        CAData foo = new CAData("CN=foo", "foo", 0, createTestCA("CN=foo", AlgorithmConstants.SIGALG_SHA256_WITH_RSA));
        CAData bar = new CAData("CN=bar", "bar", 0, createTestCA("CN=bar", AlgorithmConstants.SIGALG_SHA256_WITH_RSA));
        File exportFile = File.createTempFile("tmp", ".xml");
        performExport(Arrays.asList(foo, bar), exportFile, PERSISTENCE_UNIT, OutputFormat.XML);    
        //Use some magick to get at the method.
        Method getNextBatch =  DatabaseCliCommand.class.getDeclaredMethod("getNextBatch", XMLDecoder.class, int.class);
        getNextBatch.setAccessible(true);
        final XMLDecoder decoder = new XMLDecoder(new FileInputStream(exportFile));
        @SuppressWarnings("unchecked")
        List<CAData> results = (List<CAData>) getNextBatch.invoke(command, decoder, 10);
        int foundObjects = 0;
        for(CAData resultItem : results) {
            CAInfo resultCaInfo = resultItem.getCA().getCAInfo();
            if(resultCaInfo.equals(foo.getCA().getCAInfo())) {
                foundObjects++;
            } 
            if(resultCaInfo.equals(bar.getCA().getCAInfo())) {
                foundObjects++;
            }
        }
        assertTrue("Not all CAData objects were recovered", foundObjects == 2);
    }
    
    /**
     * Private utility method for creating a CA
     * 
     * @param cadn
     * @param sigAlg
     * @return
     * @throws Exception
     */
    private static X509CA createTestCA(final String cadn, final String sigAlg) throws Exception {
        // Create catoken
        Properties prop = new Properties();
        prop.setProperty(CATokenConstants.CAKEYPURPOSE_CERTSIGN_STRING, CAToken.SOFTPRIVATESIGNKEYALIAS);
        prop.setProperty(CATokenConstants.CAKEYPURPOSE_CRLSIGN_STRING, CAToken.SOFTPRIVATESIGNKEYALIAS); 
        prop.setProperty(CATokenConstants.CAKEYPURPOSE_DEFAULT_STRING, CAToken.SOFTPRIVATEDECKEYALIAS);
        // Set key generation property, since we have no old keys to generate the same sort
        prop.setProperty(CryptoToken.KEYSPEC_PROPERTY, "512");
        CryptoToken cryptoToken = CryptoTokenFactory.createCryptoToken(SoftCryptoToken.class.getName(), prop, null, 666);
        cryptoToken.generateKeyPair("512", CAToken.SOFTPRIVATESIGNKEYALIAS);
        cryptoToken.generateKeyPair("512", CAToken.SOFTPRIVATEDECKEYALIAS);
        
        CAToken catoken = new CAToken(cryptoToken);
        // Set key sequence so that next sequence will be 00001 (this is the default though so not really needed here)
        catoken.setKeySequence(CAToken.DEFAULT_KEYSEQUENCE);
        catoken.setKeySequenceFormat(StringTools.KEY_SEQUENCE_FORMAT_NUMERIC);
        catoken.setSignatureAlgorithm(sigAlg);
        catoken.setEncryptionAlgorithm(AlgorithmConstants.SIGALG_SHA256_WITH_RSA);

        CATokenInfo catokeninfo = catoken.getTokenInfo();
        // No extended services
        ArrayList<ExtendedCAServiceInfo> extendedcaservices = new ArrayList<ExtendedCAServiceInfo>();

        X509CAInfo cainfo = new X509CAInfo(cadn, "TEST", CAConstants.CA_ACTIVE, new Date(), "", CertificateProfileConstants.CERTPROFILE_FIXED_ROOTCA, 3650, null, // Expiretime
                CAInfo.CATYPE_X509, CAInfo.SELFSIGNED, (Collection<Certificate>) null, catokeninfo, "JUnit RSA CA", -1, null, null, // PolicyId
                24, // CRLPeriod
                0, // CRLIssueInterval
                10, // CRLOverlapTime
                10, // Delta CRL period
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
                true, // useCertReqHistory
                true, // useUserStorage
                true, // useCertificateStorage
                null //cmpRaAuthSecret
        );
        
        X509CA x509ca = new X509CA(cainfo);
        x509ca.setCAToken(catoken);
        // A CA certificate
        X509Certificate cacert = CertTools.genSelfCert(cadn, 10L, "1.1.1.1", catoken.getPrivateKey(CATokenConstants.CAKEYPURPOSE_CERTSIGN), catoken.getPublicKey(CATokenConstants.CAKEYPURPOSE_CERTSIGN), "SHA256WithRSA", true);
        assertNotNull(cacert);
        Collection<Certificate> cachain = new ArrayList<Certificate>();
        cachain.add(cacert);
        x509ca.setCertificateChain(cachain);
        // Now our CA should be operational
        return x509ca;
    }
    
    private <T> void performExport(T objectToExport, File exportFile, String persistenceUnit, OutputFormat format) throws FileNotFoundException,
            IOException, SecurityException, IllegalArgumentException, NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        List<T> exportList = new ArrayList<T>();
        exportList.add(objectToExport);
        performExport(exportList, exportFile, persistenceUnit, format);    
    }
    
    /**
     * Utility method that performs an export with mocks and stubs.
     * 
     */
    private <T> void performExport(List<T> entities, File exportFile, String persistenceUnit, OutputFormat format) throws FileNotFoundException,
            IOException, SecurityException, IllegalArgumentException, NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        EntityManagerFactory entityManagerFactoryMock = EasyMock.createMock(EntityManagerFactory.class);
        entityManagerFactoryMock.close();
        EasyMock.replay(entityManagerFactoryMock);
        
        List<String> primaryKeys = new ArrayList<String>();
        primaryKeys.add("0");
        
        Query queryMock = EasyMock.createMock(Query.class);
        EasyMock.expect(queryMock.setMaxResults(EasyMock.anyInt())).andReturn(queryMock).anyTimes();
        EasyMock.expect(queryMock.setFirstResult(0)).andReturn(queryMock).anyTimes();
        EasyMock.expect(queryMock.getResultList()).andReturn(primaryKeys);
        EasyMock.expect(queryMock.getResultList()).andReturn(entities);
        EasyMock.expect(queryMock.getResultList()).andReturn(primaryKeys);
        EasyMock.expect(queryMock.setParameter("primaryKey0", "0")).andReturn(queryMock).anyTimes();
        EasyMock.replay(queryMock);
        
        EntityManager entityManagerMock = EasyMock.createMock(EntityManager.class);    
        EasyMock.expect(entityManagerMock.createQuery(EasyMock.anyObject(String.class))).andReturn(queryMock).anyTimes();
        entityManagerMock.clear();
        entityManagerMock.clear();
        entityManagerMock.close();
        EasyMock.replay(entityManagerMock);
        command.setEntityManager(entityManagerMock, entityManagerFactoryMock, persistenceUnit);
        command.exportTable(CertificateProfileData.class, new String[]{"0"}, 1, exportFile, persistenceUnit, false, format);
        EasyMock.verify(entityManagerMock, queryMock, entityManagerFactoryMock);        
    }

}

class DatabaseCliCommandStub extends DatabaseCliCommand {
    
    private EntityManager entityManager;
    
    @Override
    public String getSubCommand() {
        return null;
    }

    @Override
    public String getMainCommand() {
        return null;
    }

    @Override
    public String getDescription() {

        return null;
    }

    @Override
    public void execute(String[] args) throws ErrorAdminCommandException {

    }
    
    @Override
    public <T> void importTable(final Class<T> c, final String[] primaryKeys, final int batchSize, final File exportFile, final String persistenceUnit) {
        super.importTable(c, primaryKeys, batchSize, exportFile, persistenceUnit);
    }

    @Override
    public <T> void exportTable(final Class<T> c, final String[] primaryKeys, final int batchSize, final File exportFile,
            final String persistenceUnit, final boolean verifyIntegrity, final OutputFormat outputFormat) {
        super.exportTable(c, primaryKeys, batchSize, exportFile, persistenceUnit, verifyIntegrity, outputFormat);
    }
    
    public void setEntityManager(EntityManager entityManager, EntityManagerFactory entityManagerFactory, String persistenceUnit) {
        this.entityManager = entityManager;
        this.entityManagerFactories.put(persistenceUnit, entityManagerFactory);
    }
    
    @Override
    protected EntityManager getEntityManager(String persistenceUnit) {
        return entityManager;
    }
    
    @SuppressWarnings("unchecked")
    public <T> List<T> getNextBatch(final ObjectInputStream ois) throws SecurityException, NoSuchMethodException, IllegalArgumentException, IllegalAccessException, InvocationTargetException {
        Method getNextBatch = DatabaseCliCommand.class.getDeclaredMethod("getNextBatch", ObjectInputStream.class, int.class);
        getNextBatch.setAccessible(true);
        return (List<T>) getNextBatch.invoke(this, ois, 1);
    }
}
