<%@page import="org.apache.struts2.components.Include"%>
<%@ page language="java" contentType="text/html; charset=UTF-8"
    pageEncoding="UTF-8"%>
<%@ taglib prefix="s" uri="/struts-tags" %>
<%@taglib prefix="bs" uri="/WEB-INF/BootStrapHandler.tld"%>
<jsp:include page="../header.jsp" />
<link href="../fileupload/css/fileinput.min.css" media="all" rel="stylesheet" type="text/css" />
<link href="../dist/css/jquery.autocomplete.css" media="all" rel="stylesheet" type="text/css" />
<link href="../dist/css/throbber.css" media="all" rel="stylesheet" type="text/css" />
<link href="../plugins/jquery-confirm/css/jquery-confirm.css" media="all" rel="stylesheet" type="text/css" />
 <link rel="stylesheet" href="../plugins/iCheck/all.css">
<style>
tr:hover{
	//font-weight: bold;
}
.tempSearch{
width:100%;
}

.text-warning{
color:#f39c12;
}
.text-success{
color:#00a65a

}
</style>

<!-- Content Wrapper. Contains page content -->
<div class="content-wrapper">
  <!-- Content Header (Page header) -->
  <section class="content-header" style="text-align: center; margin-top:-35px;">
    <h1>
    
      <i class="glyphicon glyphicon-th-list"></i>&nbsp;&nbsp;&nbsp;Assessment
      <s:if test="!(!prSubmitted || prComplete)"> <span class="text-success fa fa-eye"></span><b class="text-success"> (in Peer Review)</b></s:if>
      <s:if test="notowner"> <span class="text-warning fa fa-warning"></span><b  class="text-warning"> (Manger View)</b></s:if> <b><s:property value="assessment.appId"/> - <s:property value="assessment.name"/></b>
      <small></small>
    </h1>
  </section>

  <!-- Main content -->
  <section class="content">
  <div id="infobar" style="width:90%; margin-right:auto;margin-left:auto; display:none">
  <bs:row>

  <s:iterator value="levels" begin="9" end="0" step="-1" status="stat">
    	 	<s:if test="risk != null && risk != 'Unassigned' && risk != ''">
    	 	  <div class="col-sm-1">
				<div class="small-box" style="border-width:1px; border-style:solid;">
		            <div class="inner">
		              <h3>${counts.get(riskId)}</h3>
		              <p>${risk }</p>
		            </div>
		            <div class="icon">
		              <s:if test="risk.toLowerCase().startsWith('info') || risk.toLowerCase().startsWith('rec')">
		              	<i class="fa fa-info"></i>
		              </s:if>
		              <s:else>
		              	<i class="fa fa-bug"></i>
		              </s:else>
		            </div>
		          </div>
		         </div>
			</s:if>
</s:iterator>


</bs:row>
</div> 	
<jsp:include page="AssessmentStats.jsp" />

  
   <!-- START TABS -->
        
<div class="row">
<div class="col-md-12">
 <div class="nav-tabs-custom">
   <ul class="nav nav-tabs">
     <li><a href="Assessment#tab_1" >Overview</a></li>
     <li><a href="VulnerabilityView">Vulnerabilities</a></li>
     <li><a href="CheckList">Checklists</a></li>
     <li><a href="Assessment#tab_3" >Finalize</a></li>
     <li><a href="Assessment#tab_4" >History</a></li>
     <li class="active"><a href="AuditLog" >Audit</a></li>
   </ul>
   <div class="tab-content">
     <div class="tab-pane active">
     </div><!-- /.tab-pane -->
     <bs:datatable columns="Timestamp,Description,User" classname="" id="auditlog">
     <s:iterator value="logs">
     <tr><td>${timestamp }</td><td>${description }</td><td>${user.fname } ${user.lname }</td></tr>
     </s:iterator>
     </bs:datatable>
 
     </div><!-- /.tab-pane -->
   </div><!-- /.tab-content -->
 </div><!-- nav-tabs-custom -->
</div><!-- /.col -->
  
  
 
 

<jsp:include page="../footer.jsp" />
  

    
  
        <!-- DataTables -->
    <script src="../plugins/datatables/jquery.dataTables.min.js"></script>
    <script src="../plugins/datatables/dataTables.bootstrap.min.js"></script>
    <!-- SlimScroll -->
    <script src="../plugins/slimScroll/jquery.slimscroll.min.js"></script>
    <!-- FastClick -->
    <script src="../plugins/fastclick/fastclick.min.js"></script>
   
    
    
    <script>
function updateColors(){
    var colors = ["#8E44AD", "#9B59B6", "#2C3E50", "#34495E", "#95A5A6", "#00a65a", "#39cccc", "#00c0ef", "#f39c12", "#dd4b39"];
    var boxCount = $("#infobar").find("div.row").find("[class^=col-sm]").length;
    var width = 100/boxCount;
    $("#infobar").find("div.row").find("[class^=col-sm]").css("width", width+"%").css("min-width","100px");
    var boxes = $("#infobar").find("[class=small-box]");
    var colorCount=9;
    boxes.each((index,box) => {
            let risk = $(box).find("p")[0].innerText;
            $(`td:contains('${risk}')`).css("color", colors[colorCount]).css("font-weight", "bold");
            $(box).css("border-color",colors[colorCount]);
            $(box).css("color",colors[colorCount--]);
    });
}
    
   
    $(function(){
      updateColors()

      $("#auditlog").DataTable({
    	  "paging":   false,
      });
		
    });
    </script>
 
  </body>
</html>