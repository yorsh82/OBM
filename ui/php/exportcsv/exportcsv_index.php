<SCRIPT language="php">
///////////////////////////////////////////////////////////////////////////////
// OBM - File : exportcsv_index.php                                          //
//     - Desc : OBM CSV export Index File (used by OBM_DISPLAY)              //
// 2003-07-23 - PB - Aliacom                                                 //
///////////////////////////////////////////////////////////////////////////////
// $Id$
///////////////////////////////////////////////////////////////////////////////


///////////////////////////////////////////////////////////////////////////////
// Session, Auth, Perms Management                                           //
///////////////////////////////////////////////////////////////////////////////
$path = "..";
$section = "";
$menu = "";
$obminclude = getenv("OBM_INCLUDE_VAR");
if ($obminclude == "") $obminclude = "obminclude";
require("$obminclude/phplib/obmlib.inc");
include("$obminclude/global.inc");
page_open(array("sess" => "OBM_Session", "auth" => "OBM_Challenge_Auth", "perm" => "OBM_Perm"));
include("$obminclude/global_pref.inc");

page_close();

$params = get_param_export();

///////////////////////////////////////////////////////////////////////////////
// Main program                                                              //
//    Action : (index, document)                                             //
///////////////////////////////////////////////////////////////////////////////
if (($action == "index") || ($action == "")) {
  echo "Action incorrecte";
  dis_end();

///////////////////////////////////////////////////////////////////////////////
} elseif ($action == "message")  {
  dis_head("TÚlÚchargement");        // Head & Body
  echo "TÚlÚchargement en cours";
  dis_end();

///////////////////////////////////////////////////////////////////////////////
} elseif ($action == "export_page")  {

  $query = stripslashes($params["query"]);
  $query_pref = stripslashes($params["query_pref"]);
  $first_row = $params["first_row"];
  $nb_rows = $params["nb_rows"];

  display_debug_msg($query, $cdg_sql);
  $obm_q = new DB_OBM;
  $obm_q->query($query);

  display_debug_msg($query_pref, $cdg_sql);
  $pref_q = new DB_OBM;
  $pref_q->query($query_pref);
  
  $export_d = new OBM_DISPLAY("DATA", $pref_q);
  $export_d->data_set = $obm_q;
  header("Content-Type: text/plain");
  echo "\n\n";
  $export_d->dis_data_file($first_row, $nb_rows, ';');

}


///////////////////////////////////////////////////////////////////////////////
// Stores Export parameters transmited in $params hash
// returns : $params hash with parameters set
///////////////////////////////////////////////////////////////////////////////
function get_param_export() {
  global $first_row, $nb_rows, $query, $query_pref;
  global $cdg_param;

  if (isset ($first_row)) $params["first_row"] = $first_row;
  if (isset ($nb_rows)) $params["nb_rows"] = $nb_rows;
  if (isset ($query)) $params["query"] = $query;
  if (isset ($query_pref)) $params["query_pref"] = $query_pref;

  if ($debug > 0) {
    if ( $params ) {
      while ( list( $key, $val ) = each( $params ) ) {
        echo "<br />param[$key]=$val";
      }
      echo "<br />";
    }
  }

  return $params;
}

</SCRIPT>
