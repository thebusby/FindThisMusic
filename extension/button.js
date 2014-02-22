chrome.browserAction.onClicked.addListener(function(tab) {
    chrome.tabs.executeScript({
	code: 'document.body.style.backgroundColor="red"'
    });

    chrome.tabs.executeScript(null, {file: "script.js"});

    chrome.tabs.executeScript({
	code: 'document.body.style.backgroundColor="blue"'
    });
});
