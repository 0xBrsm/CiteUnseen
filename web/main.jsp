<%@ page contentType="text/html; charset=UTF-8" %>

<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Strict//EN"
        "http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd">
<html xmlns="http://www.w3.org/1999/xhtml" style="margin-top: 1em;">
<head>
	<title>Cite Unseen: Plagiarism Detection</title>

<link rel="stylesheet" type="text/css" href="/css/style.css"  />
<link rel="stylesheet" type="text/css" href="/css/jquery-ui.css"  />
<script type="text/javascript" src="/js/collapseSibling.js"></script>
<script type="text/javascript" src="/js/jquery-1.11.0.min.js"></script>
<script type="text/javascript" src="/js/jquery-ui.js"></script>
<script>
$(function() {
	$("#toggle-button").click(function(event) {
		$("#submission-form").toggle(0);
		$("#results-frame").toggleClass("results-frame results-frame-full");
		if ($(this).val() == "<") {
			$(this).val(">");
		} else {
			$(this).val("<");
		}
	});
	
	$("#rarity").change(function() {
        if($(this).is(":checked")) {
            $("#gaps").removeAttr("disabled");				
            $("#scoring-method").removeAttr("disabled");			
        }
        else {
            $("#gaps").attr("disabled", "disabled");		
            $("#scoring-method").attr("disabled", "disabled");
        }
    });
	
	$("form").submit(function( event ) {
		$("#results-frame").contents().find("div#results").html("<p>Processing, please wait...<p>");
	});
});
</script>

</head>
<body>
<div id="dialog" style="display: none"></div>

<div id="wrapper">

<form id="submission-form" action="results" method="post" enctype="multipart/form-data" target="results">

<div id="document-properties-container" class="properties-container">
<h1 class="label">Document</h1>

<div class="toggle-container">
	<div id="document-container" class="property-container" title="Upload a document to be analyzed.">
		<input type="file" id="document" name="document" />
	</div> <!-- document-container -->
</div>
	
</div> <!-- document-properties-container -->

<div id="scoring-properties-container" class="properties-container">
<h1 class="label" id="scoring-label">Scoring Options</h1>

<div class="toggle-container">
	<div id="score-container" class="property-container" title="Roughly equivalent to the number of consecutive words in a row. Word runs below this number of matches won't be displayed.">
		<label for="score">Minimum score</label>
		<input type="number" id="score" name="minScore" min="0" max="999" value="11" required />
	</div> <!-- score-container -->
	
	<div id="scoring-factor-container" class="property-container" title="The value of any match is multiple by this factor. For instance, values greater than 1 increase the worth of matches over mismatches.">
		<label for="scoring-factor">Weight factor</label>
		<input type="number" id="weight" name="weightFactor" min="0.1" max="9.9" value="1.2" step="0.1" required />
	</div> <!-- word-threshold-container -->
	
	<div id="rarity-container" class="property-container" title="Whether to weight the scoring by the total number of search results for an ngram. More common ngrams are less likely to be interesting and matches will be scored lower.">
		<label for="rarity">Weight by frequency</label>
		<input type="hidden" id="rarity.hidden" name="scoreByRarity" value="false" />		
		<input type="checkbox" id="rarity" name="scoreByRarity" value="true" checked />
	</div> <!-- rarity-container -->
	
	<div id="gaps-container" class="property-container" title="Whether to weight gaps by the same scoring method as matches.">
		<label for="gaps">Also weight gaps</label>
		<input type="hidden" id="gaps.hidden" name="scoreGapsByRarity" value="false" />		
		<input type="checkbox" id="gaps" name="scoreGapsByRarity" value="true" checked />
	</div> <!-- rarity-container -->	
	
</div>

</div> <!-- scoring-properties-container -->
	
<div id="advanced-properties-container" class="properties-container">
	<h1 class="label" onclick="collapseJS.toggle(this)">Advanced Options</h1>

<div class="toggle-container">
	<div id="disable-container" class="property-container" title="Disable scoring and processing. Just display the top matching web results.">
		<label for="disable">Disable scoring</label>
		<input type="hidden" id="disable.hidden" name="disableScoring" value="false" checked />		
		<input type="checkbox" id="disable" name="disableScoring" onclick="collapseJS.toggle(getElementById('scoring-label'))" value="true" />
	</div> <!-- disable-container -->
	
	<div id="citations-container" class="property-container" title="Whether to also examine description text returned with each search result for additional matches. This helps identify matching phrases, even when they include very common ngrams.">
		<label for="citations">Ignore citations</label>
		<input type="hidden" id="citations.hidden" name="ignoreCitations" value="false" />		
		<input type="checkbox" id="citations" name="ignoreCitations" value="true" checked />
	</div> <!-- citations-container -->
	
	<div id="snippet-container" class="property-container" title="Whether to also examine description text returned with each search result for additional matches. This helps identify matching phrases, even when they include very common ngrams.">
		<label for="snippet">Search snippets</label>
		<input type="hidden" id="snippet.hidden" name="snippetSearch" value="false" />		
		<input type="checkbox" id="snippet" name="snippetSearch" value="true" checked />
	</div> <!-- snippet-container -->
	
	<div id="n-container" class="property-container" title="The document will be broken down into phrases of this length (called an ngram) for searching.">
		<label for="n">Words per phrase (n)</label>
		<input type="number" id="n" name="n" min="1" max="9" value="5" />
	</div> <!-- n-container -->
	
	<div id="corpus-container" class="property-container" title="Search provider or offline corpus to search.">
<!--		<label for="search-corpus">Search</label> -->
		<select id="search-corpus" name="searchEngine">
			<option value="bing" disabled>Bing Search</option>		
			<option value="google" selected>Google Custom Search</option>
			<option value="yahoo" disabled>Yahoo! BOSS Search</option>
			<option value="COPSA" disabled>COPSA (offline)</option>
			<option value="Webis-CPC-11" disabled>Webis-CPC-11 (offline)</option>			
		</select>
	</div> <!-- word-threshold-container -->	
</div>

</div> <!-- advanced-properties-container -->
<ul id="button-choice">
	<input type="reset" id="reset" name="reset" value="Reset" />
	<input type="submit" id="submit" name="submit" value="Submit"/>
</ul>

</form> <!-- submission-form -->

<div id="toggle-button-container">
	<input type="button" id="toggle-button" name="toggle-button" value="<">
</div>

<iframe id="results-frame" class="results-frame" name="results" src="results.jsp" scrolling="no"></iframe>

</div> <!-- wrapper -->

</body>
</html>
