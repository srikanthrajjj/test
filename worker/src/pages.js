const esc = s => String(s == null ? "" : s).replace(/[&<>"']/g, c => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
const STYLE = `<meta charset=utf-8><meta name=viewport content="width=device-width,initial-scale=1"><meta name=color-scheme content="dark light">
<style>:root{color-scheme:dark light}*{box-sizing:border-box}body{margin:0;min-height:100vh;display:flex;flex-direction:column;align-items:center;justify-content:center;padding:28px;text-align:center;
font-family:-apple-system,system-ui,Roboto,"Segoe UI",sans-serif;color:#fff;background:radial-gradient(120% 80% at 50% 0%,#2a2472,#12101f 55%,#0B0B14)}
.logo{display:flex}.logo i{width:64px;height:64px;border-radius:50%;background:#7C7BFF}.logo i+i{margin-left:-22px;background:#FF6B8A;opacity:.8}
h1{font-size:30px;letter-spacing:-.8px;margin:22px 0 8px}p{color:rgba(255,255,255,.72);line-height:1.5;margin:0 0 6px;max-width:34ch}
.code{font-size:38px;font-weight:800;letter-spacing:.2em;margin:18px 0 6px}.btn{display:block;width:min(100%,320px);padding:16px;border-radius:16px;font-weight:700;font-size:17px;text-decoration:none;margin-top:12px}
.p{background:linear-gradient(135deg,#5B5BD6,#7a5af0);color:#fff}.s{background:rgba(255,255,255,.12);color:#fff}small{color:rgba(255,255,255,.5);margin-top:22px;max-width:36ch}</style>`;

export async function landing(env, url, rawCode) {
  const code = String(rawCode || "").toUpperCase().replace(/[^A-Z0-9]/g, "").slice(0, 8);
  const origin = url.origin, pkg = env.APP_PACKAGE || "com.nest.couples";
  let inviter = "Your partner", valid = false;
  if (code) {
    const c = await env.DB.prepare("SELECT id FROM couples WHERE code=?").bind(code).first();
    if (c) { valid = true; const p = await env.DB.prepare("SELECT data FROM rec WHERE couple=? AND id='profile:a'").bind(c.id).first(); if (p) inviter = JSON.parse(p.data).name || inviter; }
  }
  const store = env.PLAY_URL || env.APK_URL || "";
  const fallback = store || `${origin}/j/${code}?noapp=1`;
  const intent = `intent://join?code=${code}&s=${encodeURIComponent(origin)}#Intent;scheme=nest;package=${pkg};S.browser_fallback_url=${encodeURIComponent(fallback)};end`;
  const noapp = url.searchParams.get("noapp") === "1";
  return `<!doctype html><html lang=en><head><title>Join ${esc(inviter)} on Nest</title>${STYLE}</head><body>
<div class=logo><i></i><i></i></div>
${valid ? `<h1>${esc(inviter)} invited you</h1><p>Nest is one calm place for the two of you to see who paid what, every month.</p>
<div class=code>${esc(code)}</div><p style="font-size:13px">Your invite code</p>
<a class="btn p" id=open href="${esc(intent)}">Open in Nest</a>
${store ? `<a class="btn s" href="${esc(store)}">Get the app</a>` : `<p style="margin-top:14px;font-size:14px">Don't have the app yet? Ask ${esc(inviter)} for the install link, then open this page again.</p>`}
<small>Tip: once Nest is installed, tapping this link opens it and connects you automatically.</small>
<script>(function(){var a=/Android/i.test(navigator.userAgent);if(!a){document.getElementById('open').style.display='none';document.querySelector('small').textContent='Nest is available on Android. Open this link on your phone.';return}
${noapp ? "" : "setTimeout(function(){location.href=document.getElementById('open').href},350);"}})();</script>`
  : `<h1>Invite not found</h1><p>This invite code doesn't exist or has expired. Ask your partner to send a fresh one from Nest.</p>`}
</body></html>`;
}

export function privacy(env) {
  const contact = env.CONTACT_EMAIL || "the developer";
  return `<!doctype html><html lang=en><head><title>Nest - Privacy Policy</title><meta charset=utf-8><meta name=viewport content="width=device-width,initial-scale=1">
<style>body{font-family:-apple-system,system-ui,Roboto,sans-serif;max-width:720px;margin:0 auto;padding:28px 20px;line-height:1.6;color:#15151c}h1{letter-spacing:-.6px}h2{margin-top:30px}</style></head><body>
<h1>Nest - Privacy Policy</h1><p><em>Last updated: ${new Date().toISOString().slice(0, 10)}</em></p>
<p>Nest is a shared expense tracker for two people. This policy explains what data the app handles and why.</p>
<h2>What stays on your phone</h2><ul><li>Your expenses, settlements, names and settings are stored on your device.</li>
<li>If you choose to import bank/UPI messages, they are read and interpreted <b>on your device only</b>. The raw text of your messages is never uploaded; only the expenses you approve are saved.</li></ul>
<h2>What is sent to the server (only if you connect with your partner)</h2><ul><li>The expenses, settlements, names, colours and currency you and your partner record, so both phones show the same data.</li>
<li>A random member token that identifies your device to your shared Nest. We do not collect your phone number, email, contacts or location.</li></ul>
<h2>AI agent</h2><p>When you use the Nest agent, a summary of your shared spending (amounts, categories, merchants, names) and your question are sent to Anthropic's Claude API to generate an answer. Anthropic processes this to provide the response; see Anthropic's privacy policy.</p>
<h2>Sharing</h2><p>Your data is shared only with your partner inside your Nest. We do not sell data or use it for advertising. Invite links you send through WhatsApp or other apps are handled by those apps.</p>
<h2>Retention and deletion</h2><p>Data stays on the server until you delete it. To delete your data, use <i>You &rarr; Erase everything</i> in the app and disconnect, then contact ${esc(contact)} to purge the server copy of your Nest.</p>
<h2>Children</h2><p>Nest is not directed to children under 13.</p><h2>Contact</h2><p>Questions: ${esc(contact)}.</p></body></html>`;
}

export function deletion(env) {
  const contact = env.CONTACT_EMAIL || "the developer";
  return `<!doctype html><html lang=en><head><title>Nest - Delete your data</title><meta charset=utf-8><meta name=viewport content="width=device-width,initial-scale=1">
<style>body{font-family:-apple-system,system-ui,Roboto,sans-serif;max-width:680px;margin:0 auto;padding:28px 20px;line-height:1.6;color:#15151c}h1{letter-spacing:-.6px}code{background:#eee;padding:2px 6px;border-radius:6px}</style></head><body>
<h1>Delete your Nest data</h1>
<p>Nest has no accounts, email or phone numbers - your shared Nest is identified only by a random token on your phone. You can erase everything yourself:</p>
<ol><li>Open Nest &rarr; <b>You</b> (top right of the Month screen) &rarr; <b>Sync &amp; agent</b> &rarr; <b>Disconnect</b>.</li>
<li>Choose <b>Delete our Nest data from the server</b>. This permanently removes the shared expenses, settlements, names and your partner's access from our server.</li>
<li>To also remove data stored on your phone, use <b>Erase everything</b> in the same screen, or uninstall the app.</li></ol>
<p>Can't access the app anymore? Email ${esc(contact)} from any address with your invite code or the approximate date you created your Nest and we will erase it.</p>
<p><a href="/privacy">Privacy policy</a></p></body></html>`;
}
