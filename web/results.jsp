<%@ page contentType="text/html; charset=UTF-8" %>

<html>
<head>
	<title>Cite Unseen: Plagiarism Detection</title>
	
<link rel="stylesheet" type="text/css" href="/css/style.css"  />
<script type="text/javascript" src="/js/collapseSibling.js"></script>
<script type="text/javascript" src="/js/jquery-1.11.0.min.js"></script>
<script type="text/javascript" src="/js/jquery-ui.js"></script>
<script>
$(window).on("load resize", function(e) {
	var $text = $("#text");
	var $highlights = $("#highlights");
	var spacing = $highlights.outerHeight(true) - $highlights.height();
	var availableHeight = $(window).height() - $highlights.offset().top - spacing;

	if ($text.height() > availableHeight) {
		$highlights.outerHeight(availableHeight + spacing, true);
	}
});
$(function(){
	$("span.sequence").click(function() {
		var url = $(this).attr("url");
		var n = $(this).attr("n");
		var ngrams = $(this).attr("ngrams");
		var $jParent = window.parent.jQuery;	
		var popup = $jParent("#dialog");
		popup.dialog({
			dialogClass: "noTitle",
			draggable: false,
			modal: true,
			rizeable: true,
			open: function(){
				var overlay = $jParent(".ui-widget-overlay");
				var popup = $jParent("#dialog");
			
				var maxHeight = overlay.height()*0.9;			
				popup.dialog("option", "maxHeight", maxHeight);
				
				overlay.bind("click",function(){
					popup.dialog("destroy");
				})
			}
		});
		popup.dialog().parent().draggable();
		popup.html("Loading...");	
		
		$.post("overlap", { url:url, n:n, ngrams:ngrams }, 
			function(response) {
				popup.html("<pre>"+response+"</pre>");
			});
		return false; // prevent default
	});
});
</script>

</head>
<body>

<div id="results-container" class="properties-container" >
	<h1 class="label" onclick="collapseJS.toggle(this)">Results</h1>
	<div class="toggle-container">
		<div id="results">
			<p>${empty requestScope["results"] ? "<br>" : requestScope["results"]}</p>
		</div> <!-- info-->
	</div>

	<h1 class="label" onclick="collapseJS.toggle(this)">Marked Text</h1>
		<div class="toggle-container" id="highlights-container">
			<div id="highlights">
<!-- display text -->			
				<pre id="text">${empty requestScope["highlights"] ?
"<h3>Welcome to <b>Cite Unseen</b>, a Plagiarism Detection System</h3>
Please note that public access is currently disabled. If you'd like to arrange a test of the system or have any questions, please email <script language='JavaScript'>
document.write('<a href=\"mailto:' + 'brsm' + '@' + 'post.harvard.edu' + '\">');
document.write('brsm' + '@' + 'post.harvard.edu' + '</a>');
</script>.

A suspected document is submitted to the left for evaluation. The total number of significant URLs found and the percent match with the document will be displayed in the <b>Results</b> section above.

A marked up version of the document will also be displayed in this space after processing. Notable phrases will be <span style='background-color: yellow' title='Score of 13 with 83% similarity.' name='test' url='http://www.citeunseen.com/results.jsp' n='1' ngrams='[highlighted]'>highlighted</span> for easy visibility. Each source website is given a unique color. Hovering the cursor over a phrase will indicate the percent similarity for this website and its total score. Clicking on a link will open a window which shows the overlap between the highlighted phrase and the top matched website.

Information on the various configuration options to the left is available in the alt text visible when hovering the cursor over each option."
				: requestScope["highlights"]}</pre>
<!-- display text -->
			</div> <!-- highlights -->
		</div>
</div> <!-- results-container -->

</body>
</html>
