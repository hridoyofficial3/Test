const V="heart-orbit-v55",FILES=["./","./index.html","./style.css","./i18n.js","./app.js","./native.js","./manifest.json","./icon-192.png","./icon-512.png","./apple-touch-icon.png","./maskable-512.png"];
self.addEventListener("install",e=>{e.waitUntil(caches.open(V).then(c=>c.addAll(FILES.map(f=>new Request(f,{cache:"reload"})))).then(()=>self.skipWaiting()))});
self.addEventListener("activate",e=>{e.waitUntil(caches.keys().then(k=>Promise.all(k.filter(x=>x!==V).map(x=>caches.delete(x)))).then(()=>self.clients.claim()))});
const OK=new Set(FILES.map(f=>new URL(f,location.href).pathname));
self.addEventListener("fetch",e=>{const r=e.request,u=new URL(r.url);if(r.method!=="GET"||u.origin!==location.origin||!OK.has(u.pathname))return;
e.respondWith(caches.match(r,{ignoreSearch:true}).then(hit=>{const net=fetch(r).then(res=>{if(res.ok&&res.type==="basic"){const c=res.clone();caches.open(V).then(x=>x.put(r,c))}return res}).catch(()=>hit||(r.mode==="navigate"?caches.match("./index.html"):Response.error()));return hit||net}))});
