<?php
/*
 * Session Management for PHP3
 *
 * Copyright (c) 1998-2000 NetUSE AG
 *                    Boris Erdmann, Kristian Koehntopp
 * Copyright (c) 1999-2000 Internet Images srl
 *                    Massimiliano Masserelli
 *
 * $Id$
 *
 */ 

class Auth {
  var $classname = "Auth";
  var $persistent_slots = array("auth");
  
  var $lifetime = 0;             ## Max allowed idle time before
                                  ## reauthentication is necessary.
                                  ## If set to 0, auth never expires.
  
  var $refresh = 0;               ## Refresh interval in minutes. 
                                  ## When expires auth data is refreshed
                                  ## from db using auth_refreshlogin()
                                  ## method. Set to 0 to disable refresh

  var $mode = "log";              ## "log" for login only systems,
                                  ## "reg" for user self registration

  var $magic = "";                ## Used in uniqid() generation

  var $nobody = false;            ## If true, a default auth is created...

  var $cancel_login = "cancel_login"; ## The name of a button that can be 
                                      ## used to cancel a login form

  ## End of user qualifiable settings.

  var $auth = array();            ## Data array
  var $in;
  var $db;

  ##
  ## Initialization
  ##
  function start() {
    $cl = $this->cancel_login;
    global $sess, $$cl, $lifetime;

    // OBM : We set the auth lifetime with the global session lifetime (if set)
    // It is not set at the login (cause page_open before session_load_global)
    if ($lifetime) $this->lifetime = $lifetime;
    ## This is for performance, I guess but I'm not sure if it could
    ## be safely removed -- negro
    if (! $this->in) {
      $sess->register("auth");
      $this->in = true;
    }
    ## back compatibility: if d_c is set, create db object
    if(isset($this->database_class)) {
      $class = $this->database_class;
      $this->db = new $class;
    }

    # Check current auth state. Should be one of
    #  1) Not logged in (no valid auth info or auth expired)
    #  2) Logged in (valid auth info)
    #  3) Login in progress (if $$cl, revert to state 1)
//      echo "uid(start)=".$this->auth["uid"]."-";
    if ($this->is_authenticated()) {
      $uid = $this->auth["uid"];
//echo "<p>auth_>start : is_authenticated : ok : uid = $uid";
      switch ($uid) {
        case "form":
          # Login in progress
          if ($$cl) {
            # If $$cl is set, delete all auth info 
            # and set state to "Not logged in", so eventually
            # default or automatic authentication may take place
            $this->unauth();
            $state = 1;
          } else {
            # Set state to "Login in progress"
            $state = 3;
          }
          break;
        default:
          # User is authenticated and auth not expired
//echo "<p>User is authenticated";
          $state = 2;
          break;
      }
    } else {
      # User is not (yet) authenticated
      $this->unauth();
      $state = 1;
    }
//      echo "uid=".$this->auth["uid"]."-";

    switch ($state) {
      case 1:
//      echo "uid(cas1)=".$this->auth["uid"]."-";
        # No valid auth info or auth is expired
        
        # Check for user supplied automatic login procedure 
        if ( $uid = $this->auth_preauth() ) {
          $this->auth["uid"] = $uid;
          $this->auth["exp"] = time() + (60 * $this->lifetime);
          $this->auth["refresh"] = time() + (60 * $this->refresh);
          return true;
        }
//      echo "uid(cas1.2)=".$this->auth["uid"]."-".$this->mode."-";
        
        # Check for "log" vs. "reg" mode
        switch ($this->mode) {
          case "yes":
          case "log":
            if ($this->nobody) {
              # Authenticate as nobody
              $this->auth["uid"] = "nobody";
              # $this->auth["uname"] = "nobody";
              $this->auth["exp"] = 0x7fffffff;
              $this->auth["refresh"] = 0x7fffffff;
//      echo "uid(fcas1)=".$this->auth["uid"]."-";
              return true;
            } else {
              # Show the login form
              $this->auth_loginform();
              $this->auth["uid"] = "form";
              $this->auth["exp"] = 0x7fffffff;
              $this->auth["refresh"] = 0x7fffffff;
//      echo "uid(f2cas1)=".$this->auth["uid"]."-";
              exit;
            }
            break;
          case "reg":
            # Show the registration form
            $this->auth_registerform();
            $this->auth["uid"] = "form";
            $this->auth["exp"] = 0x7fffffff;
            $this->auth["refresh"] = 0x7fffffff;
//      echo "uid(f3cas1)=".$this->auth["uid"]."-";
            exit;
            break;
          default:
            # This should never happen. Complain.
            echo "Error in auth handling: no valid mode specified.\n";
            exit;
        }
        break;
      case 2:
        # Valid auth info
        # Refresh expire info
        ## DEFAUTH handling: do not update exp for nobody.
        if ($uid != "nobody") {
          // OBM : we get the global lifetime which is in seconds
          $this->auth["exp"] = time() + $this->lifetime;
        }
        break;
      case 3:
//      echo "uid(cas3)=".$this->auth["uid"]."-";
        # Login in progress, check results and act accordingly
        // OBM : login paramaters has been submitted
        switch ($this->mode) {
          case "yes":
          case "log":
            // OBM : if login successfull (valid auth)
            if ( $uid = $this->auth_validatelogin() ) {
              $this->auth["uid"] = $uid;

              // Domain in session
              // XXXX 2 lines could avoid query as domain_id get in auth_vali
	      $this->auth["domain_id"] = of_domain_get_entity_domain_id($this->auth["uid"], "UserObm");
              $d = of_domain_get_domain_infos($this->auth["domain_id"]);
	      $this->auth["domain_label"] = $d["label"];
							
              // OBM : in OBM each time is in seconds. this->lifetime too.
              $this->auth["exp"] = time() + $this->lifetime;
              $this->auth["refresh"] = time() + (60 * $this->refresh);
              return true;
            } else {
              $this->auth_loginform();
              $this->auth["uid"] = "form";
              $this->auth["exp"] = 0x7fffffff;
              $this->auth["refresh"] = 0x7fffffff;
              exit;
            }
            break;
          case "reg":
            if ($uid = $this->auth_doregister()) {
              $this->auth["uid"] = $uid;
              $this->auth["exp"] = time() + (60 * $this->lifetime);
              $this->auth["refresh"] = time() + (60 * $this->refresh);
              return true;
            } else {
              $this->auth_registerform();
              $this->auth["uid"] = "form";
              $this->auth["exp"] = 0x7fffffff;
              $this->auth["refresh"] = 0x7fffffff;
              exit;
            }
            break;
          default:
            # This should never happen. Complain.
            echo "Error in auth handling: no valid mode specified.\n";
            exit;
            break;
        }
        break;
      default:
        # This should never happen. Complain.
        echo "Error in auth handling: invalid state reached.\n";
        exit;
        break;
    }
  }


  function unauth($nobody = false) {
    $this->auth["uid"]   = "";
    $this->auth["perm"]  = "";
    $this->auth["exp"]   = 0;

    ## Back compatibility: passing $nobody to this method is
    ## deprecated
    if ($nobody) {
      $this->auth["uid"]   = "nobody";
      $this->auth["perm"]  = "";
      $this->auth["exp"]   = 0x7fffffff;
    }
  }
  

  function logout($nobody = "") {
    global $sess;
    
    $sess->unregister("auth");
    unset($this->auth["uname"]);
    $this->unauth($nobody == "" ? $this->nobody : $nobody);
  }

  function is_authenticated() {

    if (
      $this->auth["uid"] && 
      (($this->lifetime <= 0) || (time() < $this->auth["exp"]))) {
      
      # If more than $this->refresh minutes are passed since last check,
      # perform auth data refreshing. Refresh is only done when current
      # session is valid (registered, not expired).
      if (
        ($this->refresh > 0) 
         && 
        ($this->auth["refresh"])
         && 
        ($this->auth["refresh"] < time())
      ) {
        if ( $this->auth_refreshlogin() ) {
          $this->auth["refresh"] = time() + (60 * $this->refresh);
        } else {
         return false;
        }
      }
      
      return $this->auth["uid"];
    } else {
      return false;
    }
  }
    
  ########################################################################
  ##
  ## Helper functions
  ##

  ## This method can authenticate a user before the loginform
  ## is being displayed. If it does, it must set a valid uid 
  ## (i.e. nobody IS NOT a valid uid) just like auth_validatelogin,
  ## else it shall return false.

  function auth_preauth() { return false; }
  
  ##
  ## Authentication dummies. Must be overridden by user.
  ##
  
  function auth_loginform() { ; }

  function auth_validatelogin() { ; }
  
  function auth_refreshlogin() { ; }

  function auth_registerform() { ; }

  function auth_doregister() { ; }
}
?>
