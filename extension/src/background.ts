const CONTEXT_MENU_ID = "learnwithme-generate-question";

chrome.runtime.onInstalled.addListener(() => {
  chrome.contextMenus.create({
    id: CONTEXT_MENU_ID,
    title: "Frage erzeugen",
    contexts: ["selection"],
  });
});

chrome.sidePanel.setPanelBehavior({ openPanelOnActionClick: true }).catch(() => {
  // Ältere Chrome-Versionen ohne sidePanel-API: Panel öffnet dann nur über den Kontextmenü-Pfad unten.
});

interface CapturedSelection {
  html: string;
  text: string;
  url: string;
  title: string;
}

function captureSelection(): CapturedSelection {
  const selection = window.getSelection();
  const range = selection && selection.rangeCount > 0 ? selection.getRangeAt(0) : null;
  const container = document.createElement("div");
  if (range) container.appendChild(range.cloneContents());
  return {
    html: container.innerHTML,
    text: selection?.toString() ?? "",
    url: window.location.href,
    title: document.title,
  };
}

chrome.contextMenus.onClicked.addListener(async (info, tab) => {
  if (info.menuItemId !== CONTEXT_MENU_ID || !tab?.id) return;

  const [injection] = await chrome.scripting.executeScript({
    target: { tabId: tab.id },
    func: captureSelection,
  });

  const captured = injection?.result;
  if (!captured) return;

  await chrome.storage.session.set({ pendingSelection: captured });
  if (tab.windowId != null) {
    await chrome.sidePanel.open({ windowId: tab.windowId });
  }
});
