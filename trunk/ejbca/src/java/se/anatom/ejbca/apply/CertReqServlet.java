package se.anatom.ejbca.apply;

import java.io.*;
import java.util.*;
import java.security.cert.*;
import java.security.Provider;
import java.security.Security;
import java.security.KeyStore;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.PrivateKey;

import javax.servlet.*;
import javax.servlet.http.*;
import javax.ejb.*;

import javax.rmi.PortableRemoteObject;
import javax.naming.InitialContext;

import se.anatom.ejbca.util.Base64;

import org.apache.log4j.Logger;

import se.anatom.ejbca.ca.sign.ISignSessionHome;
import se.anatom.ejbca.ca.sign.ISignSessionRemote;
import se.anatom.ejbca.util.CertTools;
import se.anatom.ejbca.util.KeyTools;
import se.anatom.ejbca.ca.exception.AuthStatusException;
import se.anatom.ejbca.ca.exception.AuthLoginException;
import se.anatom.ejbca.ca.exception.SignRequestException;
import se.anatom.ejbca.ca.exception.SignRequestSignatureException;
import se.anatom.ejbca.ra.IUserAdminSessionRemote;
import se.anatom.ejbca.ra.IUserAdminSessionHome;
import se.anatom.ejbca.ra.UserAdminData;
import se.anatom.ejbca.SecConst;

import se.anatom.ejbca.log.Admin;

/**
 * Servlet used to install a private key with a corresponding certificate in a
 * browser. A new certificate is installed in the browser in following steps:<br>
 *
 * 1. The key pair is generated by the browser. <br>
 *
 * 2. The public part is sent to the servlet in a POST together with user info
 *    ("pkcs10|keygen", "inst", "user", "password"). For internet explorer the public key
 *    is sent as a PKCS10 certificate request. <br>
 *
 * 3. The new certificate is created by calling the RSASignSession session bean. <br>
 *
 * 4. A page containing the new certificate and a script that installs it is returned
 *    to the browser. <br>
 * <p>
 * <p>
 * The following initiation parameters are needed by this servlet: <br>
 *
 * "responseTemplate" file that defines the response to the user (IE). It should have one
 * line with the text "cert =". This line is replaced with the new certificate.
 * "keyStorePass". Password needed to load the key-store. If this parameter is none
 * existing it is assumed that no password is needed. The path could be absolute or
 * relative.<br>
 *
 * @author Original code by Lars Silv?n
 * @version $Id: CertReqServlet.java,v 1.27 2003-02-12 11:23:14 scop Exp $
 */
public class CertReqServlet extends HttpServlet {

    private static Logger log = Logger.getLogger(CertReqServlet.class);

    private ISignSessionHome signsessionhome = null;
    private IUserAdminSessionHome userdatahome;

    private byte bagattributes[] = "Bag Attributes\n".getBytes();
    private byte friendlyname[] = "    friendlyName: ".getBytes();
    private byte subject[]  = "subject=/".getBytes();
    private byte issuer[]  = "issuer=/".getBytes();
    private byte beginCertificate[] = "-----BEGIN CERTIFICATE-----".getBytes();
    private byte endCertificate[] = "-----END CERTIFICATE-----".getBytes();
    private byte beginPrivateKey[] = "-----BEGIN PRIVATE KEY-----".getBytes();
    private byte endPrivateKey[] = "-----END PRIVATE KEY-----".getBytes();
    private byte NL[] = "\n".getBytes();
    private byte boundrary[] = "outer".getBytes();

    public void init(ServletConfig config) throws ServletException {
    super.init(config);
        try {
            // Install BouncyCastle provider
            Provider BCJce = new org.bouncycastle.jce.provider.BouncyCastleProvider();
            int result = Security.addProvider(BCJce);

            // Get EJB context and home interfaces
            InitialContext ctx = new InitialContext();
            signsessionhome = (ISignSessionHome) PortableRemoteObject.narrow(
                      ctx.lookup("RSASignSession"), ISignSessionHome.class );
            userdatahome = (IUserAdminSessionHome) PortableRemoteObject.narrow(
                             ctx.lookup("UserAdminSession"), IUserAdminSessionHome.class );
        } catch( Exception e ) {
            throw new ServletException(e);
        }
    }

    public void doPost(HttpServletRequest request, HttpServletResponse response)
        throws IOException, ServletException {

        ServletDebug debug = new ServletDebug(request,response);
        try {
            String username        = request.getParameter("user");
            String password        = request.getParameter("password");
            String keylengthstring = request.getParameter("keylength");
            int keylength          = 1024;

            if(keylengthstring != null)
              keylength = Integer.parseInt(keylengthstring);

            Admin administrator =
              new Admin(Admin.TYPE_PUBLIC_WEB_USER, request.getRemoteAddr());

            IUserAdminSessionRemote adminsession = userdatahome.create();
            ISignSessionRemote signsession = signsessionhome.create();
            RequestHelper helper = new RequestHelper(administrator, debug);

            log.debug("Got request for " + username + "/" + password);
            debug.print("<h3>username: "+username+"</h3>");

            // Check user
            int tokentype = SecConst.TOKEN_SOFT_BROWSERGEN;

            UserAdminData data = adminsession.findUser(administrator, username);
            if(data == null)
              throw new ObjectNotFoundException();

                // get users Token Type.
            tokentype = data.getTokenType();
            if(tokentype == SecConst.TOKEN_SOFT_P12){
              KeyStore ks = generateToken(administrator, username, password, keylength, false);
              sendP12Token(ks, username, password, response);
            }
            if(tokentype == SecConst.TOKEN_SOFT_JKS){
              KeyStore ks = generateToken(administrator, username, password, keylength, true);
              sendJKSToken(ks, username, password, response);
            }
            if(tokentype == SecConst.TOKEN_SOFT_PEM){
              KeyStore ks = generateToken(administrator, username, password, keylength, false);
              sendPEMTokens(ks, username, password, response);
            }
            if(tokentype == SecConst.TOKEN_SOFT_BROWSERGEN){

              // first check if it is a netcsape request,
              if (request.getParameter("keygen") != null) {
                  byte[] reqBytes=request.getParameter("keygen").getBytes();
                  log.debug("Received NS request:"+new String(reqBytes));
                  if (reqBytes != null) {
                      byte[] certs = helper.nsCertRequest(signsession, reqBytes, username, password);
                      RequestHelper.sendNewCertToNSClient(certs, response);
                  }
              } else if ( (request.getParameter("pkcs10") != null) || (request.getParameter("PKCS10") != null) ) {
                  // if not netscape, check if it's IE
                  byte[] reqBytes=request.getParameter("pkcs10").getBytes();
                  if (reqBytes == null)
                      reqBytes=request.getParameter("PKCS10").getBytes();
                  log.debug("Received IE request:"+new String(reqBytes));
                  if (reqBytes != null) {
                      byte[] b64cert=helper.pkcs10CertRequest(signsession, reqBytes, username, password);
                      debug.ieCertFix(b64cert);
                      RequestHelper.sendNewCertToIEClient(b64cert, response.getOutputStream(), getServletContext(), getInitParameter("responseTemplate"));
                  }
              } else if (request.getParameter("pkcs10req") != null) {
                  // if not IE, check if it's manual request
                  byte[] reqBytes=request.getParameter("pkcs10req").getBytes();
                  if (reqBytes != null) {
                      byte[] b64cert=helper.pkcs10CertRequest(signsession, reqBytes, username, password);
                    RequestHelper.sendNewB64Cert(b64cert, response);
                  }
              }
            }
        } catch (ObjectNotFoundException oe) {
            log.debug("Non existent username!");
            debug.printMessage("Non existent username!");
            debug.printMessage("To generate a certificate a valid username and password must be entered.");
            debug.printDebugInfo();
            return;
        } catch (AuthStatusException ase) {
            log.debug("Wrong user status!");
            debug.printMessage("Wrong user status!");
            debug.printMessage("To generate a certificate for a user the user must have status new, failed or inprocess.");
            debug.printDebugInfo();
            return;
        } catch (AuthLoginException ale) {
            log.debug("Wrong password for user!");
            debug.printMessage("Wrong username or password!");
            debug.printMessage("To generate a certificate a valid username and password must be entered.");
            debug.printDebugInfo();
            return;
        } catch (SignRequestException re) {
            log.debug("Invalid request!");
            debug.printMessage("Invalid request!");
            debug.printMessage("Please supply a correct request.");
            debug.printDebugInfo();
            return;
        } catch (SignRequestSignatureException se) {
            log.debug("Invalid signature on certificate request!");
            debug.printMessage("Invalid signature on certificate request!");
            debug.printMessage("Please supply a correctly signed request.");
            debug.printDebugInfo();
            return;
        } catch (java.lang.ArrayIndexOutOfBoundsException ae) {
            log.debug("Empty or invalid request received.");
            debug.printMessage("Empty or invalid request!");
            debug.printMessage("Please supply a correct request.");
            debug.printDebugInfo();
            return;
        } catch (Exception e) {
            log.debug(e);
            debug.print("<h3>parameter name and values: </h3>");
            Enumeration paramNames=request.getParameterNames();
            while (paramNames.hasMoreElements()) {
                String name=paramNames.nextElement().toString();
                String parameter=request.getParameter(name);
                debug.print("<h4>"+name+":</h4>"+parameter+"<br>");
            }
            debug.takeCareOfException(e);
            debug.printDebugInfo();
        }
    } //doPost

    public void doGet(HttpServletRequest request,  HttpServletResponse response) throws java.io.IOException, ServletException {
        log.debug(">doGet()");
        response.setHeader("Allow", "POST");
        ServletDebug debug = new ServletDebug(request,response);
        debug.print("The certificate request servlet only handles POST method.");
        debug.printDebugInfo();
        log.debug("<doGet()");
    } // doGet

    private void sendP12Token(KeyStore ks, String username, String kspassword, HttpServletResponse out)
       throws Exception {
       ByteArrayOutputStream buffer = new ByteArrayOutputStream();
       ks.store(buffer,kspassword.toCharArray());

       out.setContentType("application/x-pkcs12");
       out.setHeader("Content-disposition", "filename=" + username + ".p12");
       out.setContentLength(buffer.size());
       buffer.writeTo(out.getOutputStream());
       out.flushBuffer();
       buffer.close();
    }

    private void sendJKSToken(KeyStore ks, String username, String kspassword,HttpServletResponse out)
       throws Exception {
       ByteArrayOutputStream buffer = new ByteArrayOutputStream();
       ks.store(buffer,kspassword.toCharArray());

       out.setContentType("application/octet-stream");
       out.setHeader("Content-disposition", "filename=" + username + ".jks");
       out.setContentLength(buffer.size());
       buffer.writeTo(out.getOutputStream());
       out.flushBuffer();
       buffer.close();
    }

    private void sendPEMTokens(KeyStore ks, String username, String kspassword,HttpServletResponse out)
       throws Exception {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        RegularExpression.RE re  = new RegularExpression.RE(",",false);
        String alias = "";

        // Find the key private key entry in the keystore
        Enumeration e = ks.aliases();
        Object o = null;
        PrivateKey serverPrivKey = null;
        while (e.hasMoreElements()) {
            o = e.nextElement();
            if(o instanceof String) {
                if ( (ks.isKeyEntry((String) o)) && ((serverPrivKey = (PrivateKey)ks.getKey((String) o, kspassword.toCharArray())) != null) ) {
                    alias = (String) o;
                    break;
                }
            }
        }

        byte privKeyEncoded[] = "".getBytes();
        if (serverPrivKey != null)
            privKeyEncoded = serverPrivKey.getEncoded();

        //Certificate chain[] = ks.getCertificateChain((String) o);
        Certificate chain[] = KeyTools.getCertChain(ks, (String) o);
        X509Certificate userX509Certificate = (X509Certificate) chain[0];

        byte output[] = userX509Certificate.getEncoded();
        String sn = userX509Certificate.getSubjectDN().toString();

        String subjectdnpem = re.replace(sn,"/");
        String issuerdnpem = re.replace(userX509Certificate.getIssuerDN().toString(),"/");

        buffer.write(bagattributes);
        buffer.write(friendlyname);
        buffer.write(alias.getBytes());
        buffer.write(NL);
        buffer.write(beginPrivateKey);
        buffer.write(NL);
        byte privKey[] = Base64.encode(privKeyEncoded);
        buffer.write(privKey);
        buffer.write(NL);
        buffer.write(endPrivateKey);
        buffer.write(NL);
        buffer.write(bagattributes);
        buffer.write(friendlyname);
        buffer.write(alias.getBytes());
        buffer.write(NL);
        buffer.write(subject);
        buffer.write(subjectdnpem.getBytes());
        buffer.write(NL);
        buffer.write(issuer);
        buffer.write(issuerdnpem.getBytes());
        buffer.write(NL);
        buffer.write(beginCertificate);
        buffer.write(NL);
        byte userCertB64[] = Base64.encode(output);
        buffer.write(userCertB64);
        buffer.write(NL);
        buffer.write(endCertificate);
        buffer.write(NL);

        if (CertTools.isSelfSigned(userX509Certificate)) {
        } else {
            for(int num = 1;num < chain.length;num++) {
                X509Certificate tmpX509Cert = (X509Certificate) chain[num];
                sn = tmpX509Cert.getSubjectDN().toString();
                String cn = CertTools.getPartFromDN(sn, "CN");

                subjectdnpem = re.replace(sn,"/");
                issuerdnpem = re.replace(tmpX509Cert.getIssuerDN().toString(),"/");

                buffer.write(bagattributes);
                buffer.write(friendlyname);
                buffer.write(cn.getBytes());
                buffer.write(NL);
                buffer.write(subject);
                buffer.write(subjectdnpem.getBytes());
                buffer.write(NL);
                buffer.write(issuer);
                buffer.write(issuerdnpem.getBytes());
                buffer.write(NL);

                byte tmpOutput[] = tmpX509Cert.getEncoded();
                buffer.write(beginCertificate);
                buffer.write(NL);
                byte tmpCACertB64[] = Base64.encode(tmpOutput);
                buffer.write(tmpCACertB64);
                buffer.write(NL);
                buffer.write(endCertificate);
                buffer.write(NL);
            }
        }

        out.setContentType("application/octet-stream");
        out.setHeader("Content-disposition", " attachment; filename=" + username + ".pem");
        buffer.writeTo(out.getOutputStream());
        out.flushBuffer();
        buffer.close();
    }



    private KeyStore generateToken(Admin administrator, String username, String password, int keylength, boolean createJKS)
       throws Exception{
         KeyPair rsaKeys = KeyTools.genKeys(keylength);
         ISignSessionRemote signsession = signsessionhome.create();
         X509Certificate cert = (X509Certificate)signsession.createCertificate(administrator, username, password, rsaKeys.getPublic());

        // Make a certificate chain from the certificate and the CA-certificate
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        Certificate[] cachain = signsession.getCertificateChain(administrator);
        // Verify CA-certificate
        if (CertTools.isSelfSigned((X509Certificate)cachain[cachain.length-1])) {
            try {
                cachain[cachain.length-1].verify(cachain[cachain.length-1].getPublicKey());
            } catch (GeneralSecurityException se) {
                throw new Exception("RootCA certificate does not verify");
            }
        }
        else
            throw new Exception("RootCA certificate not self-signed");
        // Verify that the user-certificate is signed by our CA
        try {
            cert.verify(cachain[0].getPublicKey());
        } catch (GeneralSecurityException se) {
            throw new Exception("Generated certificate does not verify using CA-certificate.");
        }

        // Use CommonName as alias in the keystore
        //String alias = CertTools.getPartFromDN(cert.getSubjectDN().toString(), "CN");
        // Use username as alias in the keystore
        String alias = username;
        // Store keys and certificates in keystore.
        KeyStore ks = null;
        if (createJKS)
            ks = KeyTools.createJKS(alias, rsaKeys.getPrivate(), password, cert, cachain);
        else
            ks = KeyTools.createP12(alias, rsaKeys.getPrivate(), cert, cachain);

        return ks;
    }

} // CertReqServlet
