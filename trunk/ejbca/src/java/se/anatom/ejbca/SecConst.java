package se.anatom.ejbca;

/**
 * Constants for users and certificates. Constants for Type of user: Type is constructed as a mask
 * since one user can be of several types. To test a user type:
 * <pre>
 * if (((type & USER_ENDUSER) == USER_ENDUSER) && ((type & USER_CAADMIN) == USER_ADMINISTOR) || ...
 *    ...
 * </pre>
 * Bit usage: bits 0-7   (1:st byte):  user types bits 8-15  (2:nd byte):  unused bits 16-23 (3:rd
 * byte):  unused bits 24-30 (4:th byte):  unused Constants for certificates are simple integer
 * types. Constants for Token Types Token type is constructed of integer constants since only one
 * token type can be generated.
 *
 * @version $Id: SecConst.java,v 1.19 2004-01-31 14:25:00 herrvendil Exp $
 */
public class SecConst extends Object {
    // User types

    /** Dummy type. */
    public static final int USER_INVALID = 0x0;

    /** This is an end user certificate (default). */
    public static final int USER_ENDUSER = 0x1;

    /** This user is an administrator. */
    public static final int USER_ADMINISTRATOR = 0x40;

    /** This users keystores are key recoverable. */
    public static final int USER_KEYRECOVERABLE = 0x80;

    /** Notification will be sent to this users emailaddress */
    public static final int USER_SENDNOTIFICATION = 0x100;

    // Old user values

/* OLD and decapriated constants.
    /** This is a CA. *
    public static final int USER_CA =             0x2;
    ** This is a RA. *
    public static final int USER_RA =             0x4;
    ** This is a Root CA. *
    public static final int USER_ROOTCA =         0x8;
    /** This is a CA Administrator. *
    public static final int USER_CAADMIN =        0x10;
    /** This is a RA Administrator. *
    public static final int USER_RAADMIN =        0x20;
*/

    /** Constants used in certificate generation and publication. */
    public static final int CERTTYPE_ENDENTITY  =     0x1;    
    public static final int CERTTYPE_SUBCA      =     0x2;
    public static final int CERTTYPE_ROOTCA     =     0x8;        
	public static final int CERTTYPE_HARDTOKEN  =     0x16;

    /** All bits used by Type. */
    public static final int USER_MASK = 0xff;

    // Token types.

    /** Indicates that a browser generated token should be used. */
    public static final int TOKEN_SOFT_BROWSERGEN = 1;

    /** Indicates that a p12 token should be generated. */
    public static final int TOKEN_SOFT_P12 = 2;

    /** Indicates that a jks token should be generated. */
    public static final int TOKEN_SOFT_JKS = 3;

    /** Indicates that a pem token should be generated. */
    public static final int TOKEN_SOFT_PEM = 4;

    /** All values equal or below this constant should be treated as a soft token. */
    public static final int TOKEN_SOFT = 100;

    /** Constant indicating a standard hard token, defined in scaper. */
    public static final int TOKEN_HARD_DEFAULT = 101;

    /** Constant indicating a eid hard token.  
     *   OBSERVE This class should only be used for backward compability with EJBCA 2.0
     */
    public static final int TOKEN_EID = 102;
    
    /**Constant indicating a swedish eid hard token.  */
    public static final int TOKEN_SWEDISHEID = 103;

    /**Constant indicating a enhanced eid hard token.  */
    public static final int TOKEN_ENHANCEDEID = 104;
    
    // Certificate profiles.

    public final static int NO_HARDTOKENISSUER            = 0;

    public final static int CERTPROFILE_FIXED_ENDUSER         = 1;
    public final static int CERTPROFILE_FIXED_SUBCA           = 2;
    public final static int CERTPROFILE_FIXED_ROOTCA          = 3;
	public final static int CERTPROFILE_FIXED_OCSPSIGNER      = 4;
	public final static int CERTPROFILE_FIXED_HARDTOKENAUTH   = 5;    
	public final static int CERTPROFILE_FIXED_HARDTOKENAUTHENC= 6;
	public final static int CERTPROFILE_FIXED_HARDTOKENENC    = 7;
	public final static int CERTPROFILE_FIXED_HARDTOKENSIGN   = 8;
    
    public final static int EMPTY_ENDENTITYPROFILE = 1; 

    public final static int ALLCAS = 1; 
        
    /**
     * Constants defining range of id's reserved for fixed certificate types. Observe fixed
     * certificates cannot have value 0.
     */
    public static final int FIXED_CERTIFICATEPROFILE_BOUNDRY = 1000;
    public static final int PROFILE_NO_PROFILE = 0;


    /** Constant used to determine the size of the result from SQL select queries */
    public static final int MAXIMUM_QUERY_ROWCOUNT = 100; 
    
    
    /** Constans used to indicate status of a CA. */
    public static final int CA_ACTIVE = 1;
    public static final int CA_WAITING_CERTIFICATE_RESPONSE = 2;
    public static final int CA_EXPIRED = 3;
    public static final int CA_REVOKED = 4;
    public static final int CA_INACTIVE = 5;
    public static final int CA_EXTERNAL = 6;


    /**
     * Prevents creation of new SecConst
     */
    private SecConst() {
    }
}


// SecConst
