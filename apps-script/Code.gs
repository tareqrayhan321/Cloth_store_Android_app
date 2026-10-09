/**
 * মেমোর ছবির স্টোরেজ — Google Apps Script (Web app)।
 * যে Google account-এর Drive-এ ছবি রাখতে চান, সেই account-এ এই স্ক্রিপ্ট deploy করুন
 * (Execute as: Me, Who has access: Anyone)। ছবি deploy করা account-এর Drive-এর
 * "ImranClothStore-Memos" ফোল্ডারে জমা হয়; এর বাইরের কোনো ফাইলে হাত দেয় না।
 */
const SECRET = 'CHANGE_ME';              // MemoConfig.kt-এর SCRIPT_KEY-এর সাথে হুবহু মিলতে হবে
const FOLDER_NAME = 'ImranClothStore-Memos';

function doGet() {
  return out_({ ok: true, msg: 'memo storage running' });
}

function doPost(e) {
  try {
    const req = JSON.parse(e.postData.contents);
    if (SECRET === 'CHANGE_ME' || req.key !== SECRET) return out_({ ok: false, error: 'unauthorized' });
    const folder = folder_();

    if (req.action === 'upload') {
      const blob = Utilities.newBlob(Utilities.base64Decode(req.data), req.mime || 'image/jpeg', req.name || 'memo.jpg');
      const file = folder.createFile(blob);
      return out_({ ok: true, id: file.getId(), size: file.getSize() });
    }

    if (req.action === 'get') {
      const file = DriveApp.getFileById(req.id);
      if (!inFolder_(file, folder)) return out_({ ok: false, error: 'not found' });
      const blob = file.getBlob();
      return out_({ ok: true, data: Utilities.base64Encode(blob.getBytes()), mime: blob.getContentType() });
    }

    if (req.action === 'delete') {
      let file;
      try { file = DriveApp.getFileById(req.id); } catch (err) { return out_({ ok: true }); } // আগেই নেই
      if (inFolder_(file, folder)) file.setTrashed(true);
      return out_({ ok: true });
    }

    return out_({ ok: false, error: 'unknown action' });
  } catch (err) {
    return out_({ ok: false, error: String(err) });
  }
}

function out_(obj) {
  return ContentService.createTextOutput(JSON.stringify(obj)).setMimeType(ContentService.MimeType.JSON);
}

function folder_() {
  const it = DriveApp.getFoldersByName(FOLDER_NAME);
  return it.hasNext() ? it.next() : DriveApp.createFolder(FOLDER_NAME);
}

function inFolder_(file, folder) {
  const parents = file.getParents();
  while (parents.hasNext()) {
    if (parents.next().getId() === folder.getId()) return true;
  }
  return false;
}
