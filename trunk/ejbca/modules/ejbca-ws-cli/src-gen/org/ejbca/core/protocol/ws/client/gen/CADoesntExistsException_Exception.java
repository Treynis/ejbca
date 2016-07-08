
package org.ejbca.core.protocol.ws.client.gen;

import javax.xml.ws.WebFault;


/**
 * This class was generated by the JAX-WS RI.
 * JAX-WS RI 2.2.4-b01
 * Generated source version: 2.2
 * 
 */
@WebFault(name = "CADoesntExistsException", targetNamespace = "http://ws.protocol.core.ejbca.org/")
public class CADoesntExistsException_Exception
    extends Exception
{

    /**
     * Java type that goes as soapenv:Fault detail element.
     * 
     */
    private CADoesntExistsException faultInfo;

    /**
     * 
     * @param message
     * @param faultInfo
     */
    public CADoesntExistsException_Exception(String message, CADoesntExistsException faultInfo) {
        super(message);
        this.faultInfo = faultInfo;
    }

    /**
     * 
     * @param message
     * @param faultInfo
     * @param cause
     */
    public CADoesntExistsException_Exception(String message, CADoesntExistsException faultInfo, Throwable cause) {
        super(message, cause);
        this.faultInfo = faultInfo;
    }

    /**
     * 
     * @return
     *     returns fault bean: org.ejbca.core.protocol.ws.client.gen.CADoesntExistsException
     */
    public CADoesntExistsException getFaultInfo() {
        return faultInfo;
    }

}