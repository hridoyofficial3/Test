/* Runs only inside the Android APK (Capacitor). In the browser/PWA it does nothing. */
(function(){
  var C=window.Capacitor;
  if(!C||!C.isNativePlatform||!C.isNativePlatform())return;
  window.HO_NATIVE=true;
  document.documentElement.classList.add("native");

  var plug=function(n){return (window.Capacitor.Plugins||{})[n]};
  var ls=function(k,v){try{if(v===undefined)return localStorage.getItem(k);localStorage.setItem(k,v)}catch(e){return null}};
  var opt=function(k){var v=ls("wh_"+k);return v===null?true:v==="1"};
  var setOpt=function(k,on){ls("wh_"+k,on?"1":"0")};
  var ui={};
  var say=function(t){if(ui.toast)ui.toast(t)};

  /* ---- open links in the phone's browser / apps ---- */
  var openExt=function(u){
    var L=plug("AppLauncher");
    if(L&&L.openUrl)return L.openUrl({url:u}).catch(function(){});
    var B=plug("Browser");
    if(B&&B.open)return B.open({url:u});
  };
  /* ---- choose which browser opens a site ---- */
  var BR_KEY="wh_browser";
  var okPkg=function(p){return typeof p==="string"&&p.length<=100&&/^[a-z][a-z0-9_]*(\.[a-z0-9_]+)+$/i.test(p)};
  var KNOWN={"com.android.chrome":"Chrome","org.mozilla.firefox":"Firefox","org.mozilla.firefox_beta":"Firefox Beta","com.brave.browser":"Brave","com.microsoft.emmx":"Edge","com.sec.android.app.sbrowser":"Samsung Internet","com.opera.browser":"Opera","com.opera.mini.native":"Opera Mini","com.kiwibrowser.browser":"Kiwi","com.duckduckgo.mobile.android":"DuckDuckGo","com.vivaldi.browser":"Vivaldi","com.UCMobile.intl":"UC Browser"};
  var brCache=null;
  var browsers=async function(){
    if(brCache)return brCache;
    var P=plug("BrowserPicker");
    if(!P||!P.getBrowsers)return [];
    try{
      var r=await P.getBrowsers();
      brCache=((r&&r.browsers)||[]).filter(function(b){return b&&okPkg(b.pkg)}).map(function(b){return {pkg:b.pkg,label:String(KNOWN[b.pkg]||b.label||b.pkg).slice(0,40)}});
      brCache.sort(function(a,b){return a.label.localeCompare(b.label)});
    }catch(e){return []}
    return brCache;
  };
  var openIn=function(u,pkg){
    var P=plug("BrowserPicker");
    if(P&&P.open&&okPkg(pkg))return P.open({url:u,pkg:pkg}).then(function(r){
      if(r&&r.fallback)say("ওই ব্রাউজার পাওয়া যায়নি, সাধারণভাবে খোলা হয়েছে");
    }).catch(function(){return openExt(u)});
    return openExt(u);
  };
  /* per-site browser wins, then the global default, then the phone's own default */
  window.HO_openSite=function(u,pkg){
    var p=okPkg(pkg)?pkg:ls(BR_KEY);
    return okPkg(p)?openIn(u,p):openExt(u);
  };
  /* ---- sites as app windows (no browser bar) / home-screen icons ---- */
  var webOpen=function(u,t){
    var P=plug("WebApp");
    if(P&&P.open)return P.open({url:u,title:t||""}).catch(function(){return window.HO_openSite(u)});
    return window.HO_openSite(u);
  };
  var webAdd=function(o){
    var P=plug("WebApp");
    if(!P||!P.addShortcut)return Promise.reject(new Error("no plugin"));
    return P.addShortcut(o);
  };
  window.open=function(u){
    try{
      u=String(u||"");
      var m=u.match(/^intent:.*?S\.browser_fallback_url=([^;]+)/i);
      if(m)u=decodeURIComponent(m[1]);
      if(/^https?:\/\//i.test(u))window.HO_openSite(u);
    }catch(e){}
    return null;
  };

  /* ---- save backup / CSV through the share sheet ---- */
  window.HO_save=async function(name,text){
    var F=plug("Filesystem"),S=plug("Share");
    if(!F||!S)return false;
    try{
      var r=await F.writeFile({path:name,data:text,directory:"CACHE",encoding:"utf8",recursive:true});
      await S.share({title:name,dialogTitle:name,files:[r.uri]});
      return true;
    }catch(e){
      return /cancel/i.test(String((e&&e.message)||e));
    }
  };

  /* ---- weekly backup reminder (local notification, Friday 8 PM) ---- */
  var BK_ID=1001;
  async function schedBk(on,askPerm){
    var N=plug("LocalNotifications");
    if(!N)return false;
    try{
      await N.cancel({notifications:[{id:BK_ID}]});
      if(!on)return true;
      var p=await N.checkPermissions();
      if(p.display!=="granted"){
        if(!askPerm)return false;
        p=await N.requestPermissions();
        if(p.display!=="granted")return false;
      }
      await N.schedule({notifications:[{
        id:BK_ID,
        title:"Heart Orbit",
        body:"সপ্তাহের ব্যাকআপ নেওয়ার সময় হয়েছে। সেটিংস থেকে ব্যাকআপ নিয়ে ডেটা সেভ করে রাখুন।",
        schedule:{on:{weekday:6,hour:20,minute:0}}
      }]});
      return true;
    }catch(e){return false}
  }

  /* ---- update check (GitHub latest release) ---- */
  async function getJson(url){
    var H=plug("CapacitorHttp");
    if(H&&H.get){
      var r=await H.get({url:url,headers:{Accept:"application/vnd.github+json"}});
      if(r.status!==200)throw new Error("http "+r.status);
      return typeof r.data==="string"?JSON.parse(r.data):r.data;
    }
    var f=await fetch(url,{headers:{Accept:"application/vnd.github+json"}});
    if(!f.ok)throw new Error("http "+f.status);
    return f.json();
  }
  function showUpdate(latest,url){
    ui.openSheet('<h3>নতুন ভার্সন এসেছে</h3>'
      +'<p>Heart Orbit-এর নতুন সংস্করণ (1.'+latest+') ডাউনলোডের জন্য প্রস্তুত। বাটন চাপলে ডাউনলোড শুরু হবে; শেষ হলে ফাইলটি খুলে ইনস্টল করুন। আপনার ডেটা থেকে যাবে।</p>'
      +'<p class="hint">আপডেটের আগে সেটিংস থেকে একটি ব্যাকআপ নিয়ে রাখা ভালো।</p>'
      +'<div class="row"><button class="btn" data-close="1">পরে</button><button class="btn p" id="hoUpGo">আপডেট করুন</button></div>');
    var b=document.getElementById("hoUpGo");
    if(b)b.onclick=function(){openExt(url);if(ui.closeSheet)ui.closeSheet()};
  }
  async function check(manual){
    var B=window.HO_BUILD;
    if(!B||!B.repo||!B.n){if(manual)say("এই ভার্সনে আপডেট চেক নেই");return}
    if(!manual){
      if(!opt("n_up"))return;
      if(Date.now()-(+ls("wh_up_t")||0)<432e5)return;
    }
    var rel;
    try{rel=await getJson("https://api.github.com/repos/"+B.repo+"/releases/latest")}
    catch(e){if(manual)say("আপডেট চেক করা যায়নি। ইন্টারনেট দেখুন");return}
    var m=/(\d+)\s*$/.exec(String((rel&&rel.tag_name)||"")),latest=m?+m[1]:0;
    if(latest<=B.n){ls("wh_up_t",String(Date.now()));if(manual)say("আপনি সর্বশেষ ভার্সনেই আছেন");return}
    var url=rel.html_url,a=(rel.assets||[]).filter(function(x){return /\.apk$/i.test(x.name||"")})[0];
    if(a&&a.browser_download_url)url=a.browser_download_url;
    if(!/^https:\/\/github\.com\//.test(String(url)))return;
    var ov=document.getElementById("ov");
    if(!manual&&ov&&ov.classList.contains("show"))return;
    ls("wh_up_t",String(Date.now()));
    showUpdate(latest,url);
  }

  window.HO_native={
    denied:false,
    bkOn:function(){return opt("n_bk")},
    upOn:function(){return opt("n_up")},
    toggleBk:async function(){
      var on=!opt("n_bk"),ok=await schedBk(on,true);
      if(on&&!ok){this.denied=true;setOpt("n_bk",false);return false}
      this.denied=false;setOpt("n_bk",on);
      return on;
    },
    toggleUp:function(){var on=!opt("n_up");setOpt("n_up",on);return on},
    browsers:browsers,
    webOpen:webOpen,
    webAdd:webAdd,
    getDefault:function(){var v=ls(BR_KEY);return okPkg(v)?v:""},
    setDefault:function(p){if(okPkg(p))ls(BR_KEY,p);else try{localStorage.removeItem(BR_KEY)}catch(e){}},
    label:function(p){
      if(KNOWN[p])return KNOWN[p];
      var b=(brCache||[]).filter(function(x){return x.pkg===p})[0];
      return b?b.label:String(p||"");
    },
    check:check,
    init:function(h){
      ui=h||{};
      if(opt("n_bk")){
        var asked=ls("wh_n_ask");
        schedBk(true,!asked).then(function(){ls("wh_n_ask","1")});
      }
      setTimeout(function(){check(false)},4000);
    }
  };
})();
