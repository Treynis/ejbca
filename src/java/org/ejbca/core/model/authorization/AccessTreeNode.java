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
 
package org.ejbca.core.model.authorization;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;

import org.apache.log4j.Logger;

/**
 * The building component of the AccessTree. All nodes consist of these objects.
 *
 * @author  Philip Vendil
 * @version $Id$
 */
public class AccessTreeNode implements Serializable{

    private static Logger log = Logger.getLogger(AccessTreeNode.class);


    // Private Constants
    // OBSERVE that the order is important!!
    public static final int STATE_UNKNOWN = 1;
    public static final int STATE_ACCEPT = 2;
    public static final int STATE_ACCEPT_RECURSIVE = 3;
    public static final int STATE_DECLINE = 4;
    public static final int STATE_DECLINE_RECURSIVE = 5;

    /** Creates a new instance of AccessTreeNode */
    public AccessTreeNode(String resource) {
        //log.debug(">AccessTreeNode:" +resource);
        name=resource;
        useraccesspairs = new ArrayList();
        leafs = new HashMap();
    }

    /** Checks the tree if the users X509Certificate is athorized to view the requested resource */
    public boolean isAuthorized(AdminInformation admininformation, String resource) {
    	if (log.isDebugEnabled()) {
            log.debug(">isAuthorized: " +resource);    		
    	}
        boolean retval =isAuthorizedRecursive(admininformation,resource,STATE_DECLINE); // Default is to decline access.
    	if (log.isDebugEnabled()) {
    		log.debug("<isAuthorized: returns " + retval);
    	}
        return retval;
    }

     /** Adds an access rule with associated admingroup to the tree. */
     public void addAccessRule(String subresource, AccessRule accessrule, AdminGroup admingroup) {
    	 if (log.isDebugEnabled()) {
    		 log.debug(">addAccessRule: " + subresource );
    	 }
       int index;
       AccessTreeNode next;
       String nextname;
       String nextsubresource;

       if(subresource.equals(this.name)){ // Root is a special case.
           Object[] accessadmingroupair = {accessrule,admingroup};
           useraccesspairs.add(accessadmingroupair);
       }
       else{
           nextsubresource = subresource.substring(this.name.length());
           if((nextsubresource.toCharArray()[0])=='/')
             nextsubresource = nextsubresource.substring(1);

           index = nextsubresource.indexOf('/');
           if(index != -1){
             nextname =  nextsubresource.substring(0,index);
           }
           else{
             nextname = nextsubresource;
           }
           next= (AccessTreeNode) leafs.get(nextname);
           if(next == null){  // Doesn't exist, create.
              next=new AccessTreeNode(nextname);
              leafs.put(nextname, next);
           }        
           //log.debug(this.name + " --> ");
                    
           next.addAccessRule(nextsubresource, accessrule, admingroup);
       }
       if (log.isDebugEnabled()) {
    	   log.debug("<addAccessRule: " + subresource);
       }
     }

    private boolean isAuthorizedRecursive(AdminInformation admininformation, String resource, int state){
    	if (log.isDebugEnabled()) {
    		log.debug("isAuthorizedRecursive: " + " resource: " + resource + " name: "+ this.name + "," +state);
    	}
       int index;
       int internalstate = STATE_DECLINE;
       boolean returnval = false;
       AccessTreeNode next;
       String nextname = null;
       String nextsubresource;

       internalstate = matchInformation(admininformation);    
       if(resource.equals(this.name)) {        
         // If this resource have state accept recursive state is given
         if( state == STATE_ACCEPT_RECURSIVE || internalstate == STATE_ACCEPT || internalstate == STATE_ACCEPT_RECURSIVE ){
             // If this resource's rule set don't says decline.
           if(!(internalstate == STATE_DECLINE || internalstate == STATE_DECLINE_RECURSIVE))
             returnval=true;
         }
       }
       else{
         //log.debug(" resource : " + resource);
         nextsubresource = resource.substring(this.name.length());
         if((nextsubresource.toCharArray()[0])=='/')
         nextsubresource = nextsubresource.substring(1);
         //log.debug(" nextresource : " + nextsubresource);
         
         index = nextsubresource.indexOf('/');
         if(index != -1){
             nextname =  nextsubresource.substring(0,index);
         }
         else {
           nextname = nextsubresource; 
         }
         //log.debug(" nextname : " + nextname);
         
         next = (AccessTreeNode) leafs.get(nextname);
         if(next == null ){  // resource path doesn't exist

            // If  internal state isn't decline recusive is accept recursive.
            if(internalstate == STATE_ACCEPT_RECURSIVE){
               returnval=true;
            }
            // If state accept recursive is given and internal state isn't decline recusive.
            if(state == STATE_ACCEPT_RECURSIVE  && internalstate != STATE_DECLINE_RECURSIVE && internalstate != STATE_DECLINE){
              returnval=true;
            }
       /*     if(internalstate == STATE_ACCEPT && lastresource){
              returnval=true;
            } */
         }
         if(next != null){ // resource path exists.
           // If internalstate is accept recursive or decline recusive.
           if(internalstate == STATE_ACCEPT_RECURSIVE || internalstate == STATE_DECLINE_RECURSIVE){
             state=internalstate;
           }
           //log.debug(this.name + " --> ");
           returnval=next.isAuthorizedRecursive(admininformation, nextsubresource, state);
         }
       }
       if (log.isDebugEnabled()) {
    	   log.debug("<isAthorizedRecursive: returns " + returnval + " : " + resource + "," +state);
       }
       return returnval;
    }

       private int matchInformation(AdminInformation admininformation){
    	   if (log.isDebugEnabled()) {
    		   log.debug(">matchInformation");
    	   }
          final int ACCESSRULE = 0;
          final int ADMINGROUP  = 1;

          int state     = STATE_UNKNOWN;
          int stateprio = 0;
          Object[] accessuserpair;
          Collection adminentities;
           
          for (int i = 0; i < useraccesspairs.size();i++){ 
            accessuserpair = (Object[]) useraccesspairs.get(i);
            if(admininformation.isGroupUser()){
              if(((AdminGroup) accessuserpair[ADMINGROUP]).getAdminGroupId() == admininformation.getGroupId()){	              
			    state = ((AccessRule) accessuserpair[ACCESSRULE]).getRuleState();
              }	
            }else{                                    
              adminentities = ((AdminGroup) accessuserpair[ADMINGROUP]).getAdminEntities();
              Iterator iter = adminentities.iterator();
              while(iter.hasNext()){
                AdminEntity adminentity = (AdminEntity) iter.next();  
                // If user entity match.
                if(adminentity.match(admininformation)){
                  int thisuserstate = ((AccessRule) accessuserpair[ACCESSRULE]).getRuleState();
                  int thisuserstateprio = adminentity.getPriority();
                  // If rule has higher priority, it's state is to be used.
                  if( stateprio < thisuserstateprio){
                    state=thisuserstate;
                    stateprio=thisuserstateprio;
                  }
                  else{
                    if( stateprio == thisuserstateprio){
                      // If the priority is the same then decline has priority over accept.
                      if(state < thisuserstate){
                        state=thisuserstate;
                      }
                    }
                  }
                }
              }
            }
          }  
          if (log.isDebugEnabled()) {
        	  log.debug("<matchInformation: returns " + state );
          }
          return state;
       }

    // Private fields.
    private String  name;
    private ArrayList  useraccesspairs;
    private HashMap leafs;

}
