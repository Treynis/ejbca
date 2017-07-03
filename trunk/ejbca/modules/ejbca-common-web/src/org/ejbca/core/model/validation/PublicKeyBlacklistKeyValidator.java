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

package org.ejbca.core.model.validation;

import java.security.PublicKey;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPublicKey;
import org.bouncycastle.jcajce.provider.asymmetric.rsa.BCRSAPublicKey;
import org.cesecore.keys.util.KeyTools;
import org.cesecore.keys.validation.KeyValidationException;
import org.cesecore.keys.validation.KeyValidatorBase;
import org.cesecore.profiles.Profile;
import org.cesecore.util.CertTools;
import org.ejbca.core.model.util.EjbLocalHelper;

/**
 * Public key blacklist key validator using the Bouncy Castle BCRSAPublicKey implementation 
 * (see org.bouncycastle.jcajce.provider.asymmetric.rsa.BCRSAPublicKey). 
 * 
 * The key validator is used to filter out weak debian keys or other blacklisted public keys 
 * generated by 'openssl', 'ssh-keygen', or 'openvpn --keygen', see {@link https://wiki.debian.org/SSLkeys#Identifying_Weak_Keys}
 * 
 * @version $Id$
 */
public class PublicKeyBlacklistKeyValidator extends KeyValidatorBase {

    private static final long serialVersionUID = 215729318959311916L;

    /** List separator. */
    private static final String LIST_SEPARATOR = ";";

    /** Class logger. */
    private static final Logger log = Logger.getLogger(PublicKeyBlacklistKeyValidator.class);

    public static final float LATEST_VERSION = 1F;

    /** The key validator type. */
    private static final String TYPE_IDENTIFIER = "BLACKLIST_KEY_VALIDATOR";

    /** View template in /ca/editkeyvalidators. */
    protected static final String TEMPLATE_FILE = "editBlacklistKeyValidator.xhtml";

    protected static final String KEY_GENERATOR_SOURCES = "keyGeneratorSources";

    protected static final String KEY_ALGORITHMS = "keyAlgorithms";
    
    /** field used for JUnit testing, avoiding lookups so we can control the cache */
    private boolean useOnlyCache = false;
    
    /**
     * Public constructor needed for deserialization.
     */
    public PublicKeyBlacklistKeyValidator() {
        super();
        init();
    }
    
    /**
     * Creates a new instance.
     */
    public PublicKeyBlacklistKeyValidator(final String name) {
        super(name);
        init();
    }

    /**
     * Initializes uninitialized data fields.
     */
    public void init() {
        super.init();
        if (data.get(KEY_GENERATOR_SOURCES) == null) {
            setKeyGeneratorSources(new ArrayList<String>()); // KeyGeneratorSources.sourcesAsString()
        }
        if (data.get(KEY_ALGORITHMS) == null) {
            setKeyAlgorithms(new ArrayList<String>());
        }
    }

    /** Gets a list of key generation sources.
     * @return a list of key generation source indexes.
     */
    public List<Integer> getKeyGeneratorSources() {
        final String value = (String) data.get(KEY_GENERATOR_SOURCES);
        final List<Integer> result = new ArrayList<Integer>();
        if (StringUtils.isNotBlank(value)) {
            final String[] tokens = value.trim().split(LIST_SEPARATOR);
            for (int i = 0, j = tokens.length; i < j; i++) {
                result.add(Integer.valueOf(tokens[i]));
            }
        }
        return result;
    }

    /** Sets key generation source indexes.
     * 
     * @param indexes list of key generation source indexes.
     */
    public void setKeyGeneratorSources(List<String> indexes) {
        final StringBuilder builder = new StringBuilder();
        for (String index : indexes) {
            if (builder.length() == 0) {
                builder.append(index);
            } else {
                builder.append(LIST_SEPARATOR).append(index);
            }
        }
        data.put(KEY_GENERATOR_SOURCES, builder.toString());
    }

    /** Gets a list of key algorithms.
     * @return a list.
     */
    public List<String> getKeyAlgorithms() {
        final String value = (String) data.get(KEY_ALGORITHMS);
        final List<String> result = new ArrayList<String>();
        final String[] tokens = value.trim().split(LIST_SEPARATOR);
        for (int i = 0, j = tokens.length; i < j; i++) {
            result.add(tokens[i]);
        }
        return result;
    }

    /** Sets the key algorithms.
     * 
     * @param algorithms list of key algorithms.
     */
    public void setKeyAlgorithms(List<String> algorithms) {
        final StringBuilder builder = new StringBuilder();
        for (String index : algorithms) {
            if (builder.length() == 0) {
                builder.append(index);
            } else {
                builder.append(LIST_SEPARATOR).append(index);
            }
        }
        data.put(KEY_ALGORITHMS, builder.toString());
    }

    @Override
    public String getTemplateFile() {
        return TEMPLATE_FILE;
    }
    
    @Override
    public float getLatestVersion() {
        return LATEST_VERSION;
    }

    @Override
    public void upgrade() {
        super.upgrade();
        if (log.isTraceEnabled()) {
            log.trace(">upgrade: " + getLatestVersion() + ", " + getVersion());
        }
        if (Float.compare(LATEST_VERSION, getVersion()) != 0) {
            // New version of the class, upgrade.
            log.info(intres.getLocalizedMessage("blacklistkeyvalidator.upgrade", new Float(getVersion())));
            init();
        }
    }

    @Override
    public void before() {
        if (log.isDebugEnabled()) {
            log.debug("BlacklistKeyValidator before.");
            // Initialize used objects here.
        }
    }

    @Override
    public boolean validate(final PublicKey publicKey) throws KeyValidationException {
        super.validate(publicKey);
        final int keyLength = KeyTools.getKeyLength(publicKey);
        final String keyAlgorithm = publicKey.getAlgorithm(); // AlgorithmTools.getKeyAlgorithm(publicKey);
        if (log.isDebugEnabled()) {
            log.debug("Validating public key with algorithm " + keyAlgorithm + ", length " + keyLength + ", format " + publicKey.getFormat()
                    + ", implementation " + publicKey.getClass().getName() + " against public key blacklist.");
        }
        final String fingerprint = CertTools.createPublicKeyFingerprint(publicKey, PublicKeyBlacklistEntry.DIGEST_ALGORITHM);
        log.info("Matching public key with fingerprint " + fingerprint + " with public key blacklist.");
        if (!useOnlyCache) {
            // A bit hackish, make a call to blacklist session to ensure that blacklist cache has this entry loaded
            // this call is made here, even if the Validator does not use blacklists, but Validator can not call an EJB so easily.
            // and we don't want to do instanceof, so we take the hit
            // TODO: if the key is not in the cache (which it hopefully is not) this is a database lookup for each key. Huuge performance hit
            // should better be implemented as a full in memory cache with a state so we know if it's loaded or not, with background updates. See ECA-5951
            new EjbLocalHelper().getPublicKeyBlacklistSession().getPublicKeyBlacklistEntryId(CertTools.createPublicKeyFingerprint(publicKey, PublicKeyBlacklistEntry.DIGEST_ALGORITHM));
        }
        Integer idValue = PublicKeyBlacklistEntryCache.INSTANCE.getNameToIdMap().get(fingerprint);
        final PublicKeyBlacklistEntry entry = PublicKeyBlacklistEntryCache.INSTANCE.getEntry(idValue);
        boolean keyGeneratorSourceMatched = false;
        boolean keySpecMatched = false;

        if (null != entry) {
            // Filter for key generator sources.
            if (getKeyGeneratorSources().contains(new Integer(-1)) || getKeyGeneratorSources().contains(Integer.valueOf(entry.getSource()))) {
                keyGeneratorSourceMatched = true;
            }
            // Filter for key specifications.
            if (getKeyAlgorithms().contains("-1") || getKeyAlgorithms().contains(getKeySpec(publicKey))) {
                keySpecMatched = true;
            }
        }
        if (keyGeneratorSourceMatched && keySpecMatched) {
            final String message = "Public key with id " + entry.getID() + " and fingerprint " + fingerprint
                    + " found in public key blacklist.";
            if (log.isDebugEnabled()) {
                log.debug(message);
            }
            messages.add("Invalid: " + message);
        }

        if (log.isTraceEnabled()) {
            for (String message : getMessages()) {
                log.trace(message);
            }
        }
        return getMessages().size() == 0;
    }

    @Override
    public void after() {
        if (log.isDebugEnabled()) {
            log.debug("BlacklistKeyValidator after.");
            // Finalize used objects here.
        }
    }

    private final String getKeySpec(PublicKey publicKey) {
        String keySpec;
        if (publicKey instanceof BCRSAPublicKey) {
            keySpec = publicKey.getAlgorithm().toUpperCase() + Integer.toString(((BCRSAPublicKey) publicKey).getModulus().bitLength());
        } else if (publicKey instanceof BCECPublicKey) {
            keySpec = publicKey.getAlgorithm().toUpperCase();
        } else {
            keySpec = publicKey.getAlgorithm().toUpperCase();
        }
        if (log.isTraceEnabled()) {
            log.trace("Key specification " + keySpec + " determined for public key " + publicKey.getEncoded());
        }
        return keySpec;
    }

    protected void setUseOnlyCache(boolean useOnlyCache) {
        this.useOnlyCache = useOnlyCache;
    }

    @Override
    public String getValidatorTypeIdentifier() {
        return TYPE_IDENTIFIER;
    }

    @Override
    public String getLabel() {
        return intres.getLocalizedMessage("validator.implementation.key.blacklist");
    }

    @Override
    protected Class<? extends Profile> getImplementationClass() {
        return PublicKeyBlacklistKeyValidator.class;
    }
    
}