//collapse all siblings other than this one
//use with a heading that is the first child element of a div
// assumes all siblings are block - could check but it's designed for a simple case

var collapseJS={
	
	initialize: function(){
	//add event listeners to all toggle containers
	
		var containers=document.querySelectorAll('.toggle-container');
	
		for (var i=0; i < containers.length; i++) {
				containers[i].addEventListener("webkitTransitionEnd", collapseJS.changeHeightFinished)	
				containers[i].addEventListener("MozTransitionEnd", collapseJS.changeHeightFinished)	
		};

	},

	toggle: function(element){
		//collapse if expanded and vv
		var nextSibling=element.nextElementSibling;
		if (nextSibling){
			var ht=nextSibling.style.height;
			if(nextSibling.style.height=="0px"){
				// nextSibling.style.webkitTransition='height .5s';
				// nextSibling.style.height=nextSibling.getAttribute('data-lastHeight')+'px';
				// nextSibling.style.webkitTransition='none';
				nextSibling.style.height='auto';
				element.className="";
			}
			else {
				nextSibling.setAttribute('data-lastHeight', nextSibling.clientHeight);
				// nextSibling.style.height=nextSibling.clientHeight+'px';
				// nextSibling.style.webkitTransition='height .5s';
				
				nextSibling.style.height="0px";
				element.className="closed"
						
			}
		}
		
	},
	
	changeHeightFinished: function(e){
		this.style.WebkitTransition='height 0';

		if (this.style.height!='0px'){
			this.style.WebkitTransition='';
			this.style.height="auto";
		}
		
		else {
			this.style.WebkitTransition='';
			this.style.height="0";
			
		}
	},
	
	
	collapse: function(element){
		//collapse all siblings of this element
		var parentElement=element.parentNode;
		if (parentElement){
			var children=parentElement.children;
			for (var i=1; i < children.length; i++) {
				children[i].style.display="none"
			};
		}
	},
	
	expand: function(element){
		//expand all siblings of this element
		var parentElement=element.parentNode;
		if (parentElement){
			var children=parentElement.childNodes;
			for (var i=1; i < children.length; i++) {
				children[i].style.display="block"
			};
		}
	},
}