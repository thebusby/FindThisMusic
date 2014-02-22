function findThisMusic()
{
    var markup = document.documentElement.innerHTML;

    var req = new XMLHttpRequest();
    req.open("POST", "http://apaato.thebusby.com:5223/findthismusic/" , true);
    req.onload = function(e) {
	alert(this.responseText);
    }
    req.send( markup );
}

findThisMusic();
