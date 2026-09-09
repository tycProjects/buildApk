const $=id=>document.getElementById(id);
const video=$('preview'),canvas=$('canvas'),capture=$('capture'),thumb=$('thumb'),gallery=$('gallery'),galleryEmpty=$('galleryEmpty'),flip=$('flip'),flash=$('flash'),settings=$('settings'),panel=$('panel'),close=$('close'),grid=$('grid'),sound=$('sound'),fiftyM=$('fiftyM'),mirror=$('mirror'),quality=$('quality'),timerSelect=$('timerSelect'),msg=$('msg'),countdown=$('countdown');
let stream=null,facing='environment',mode='photo',zoom=1,recording=false,rec=null,chunks=[],mediaItems=[],selectedIndex=-1,flashOn=false,db=null;

function say(t){msg.textContent=t;msg.style.display='block';clearTimeout(say.t);say.t=setTimeout(()=>msg.style.display='none',2300)}
function stopStream(){if(stream){stream.getTracks().forEach(t=>t.stop());stream=null}}
async function startCamera(){
  stopStream();
  if(!navigator.mediaDevices?.getUserMedia)return say('Camera access is not available in this browser.');
  try{
    const high=fiftyM.checked;
    const constraints={video:{facingMode:facing,width:{ideal:high?8192:3840},height:{ideal:high?6144:2160}},audio:mode==='video'};
    stream=await navigator.mediaDevices.getUserMedia(constraints);
    video.srcObject=stream;
    await video.play();
    applyZoom(zoom);
    if(facing==='user') video.style.transform='scaleX(-1)';
    else video.style.transform='';
  }catch(e){say('Allow camera and microphone permissions, then try again.')}
}
async function applyZoom(z){
  zoom=z;
  $('lensLabel').textContent=(z===1?'1×':z+'×');
  const t=stream?.getVideoTracks()[0],caps=t?.getCapabilities?.();
  if(caps?.zoom){
    try{await t.applyConstraints({advanced:[{zoom:Math.max(caps.zoom.min,Math.min(caps.zoom.max,z))}]});video.style.transform=facing==='user'?'scaleX(-1)':'scale(1)';return}catch(e){}
  }
  video.style.transform=facing==='user'?`scaleX(-1) scale(${z})`:`scale(${z})`;
}
document.querySelectorAll('#zoomRail button').forEach(b=>b.onclick=()=>{document.querySelectorAll('#zoomRail button').forEach(x=>x.classList.remove('active'));b.classList.add('active');applyZoom(+b.dataset.z)});
video.addEventListener('click',e=>{
  const r=video.getBoundingClientRect(),ring=$('focusRing');
  ring.style.left=(e.clientX-r.left)+'px';ring.style.top=(e.clientY-r.top)+'px';ring.style.opacity=1;
  setTimeout(()=>ring.style.opacity=0,650);
});
settings.onclick=()=>{panel.classList.add('open');panel.setAttribute('aria-hidden','false')};
close.onclick=()=>{panel.classList.remove('open');panel.setAttribute('aria-hidden','true')};
grid.onchange=()=>document.querySelector('#gridline').classList.toggle('on',grid.checked);
fiftyM.onchange=()=>{if(mode==='photo'||mode==='50m')startCamera()};
mirror.onchange=()=>{if(facing==='user')applyZoom(zoom)};
flip.onclick=()=>{if(recording)return;facing=facing==='environment'?'user':'environment';startCamera()};
flash.onclick=async()=>{
  const t=stream?.getVideoTracks()[0],caps=t?.getCapabilities?.();
  if(!caps?.torch)return say('Flash control is not supported on this camera.');
  flashOn=!flashOn;flash.textContent=flashOn?'ON':'AUTO';
  try{await t.applyConstraints({advanced:[{torch:flashOn}]})}catch(e){say('Could not change flash mode.')}
};
function shutterSound(){
  if(!sound.checked)return;
  try{const AC=window.AudioContext||window.webkitAudioContext,ac=new AC(),o=ac.createOscillator(),g=ac.createGain();o.frequency.value=1050;g.gain.value=.045;o.connect(g);g.connect(ac.destination);o.start();o.stop(ac.currentTime+.07)}catch(e){}
}
function wait(ms){return new Promise(r=>setTimeout(r,ms))}
async function takePhoto(){
  if(!video.videoWidth)return say('Camera is not ready.');
  const secs=+timerSelect.value;
  if(secs){countdown.classList.remove('hidden');for(let n=secs;n>0;n--){countdown.textContent=n;await wait(1000)}countdown.classList.add('hidden')}
  canvas.width=video.videoWidth;canvas.height=video.videoHeight;
  const x=canvas.getContext('2d');
  if(facing==='user' && mirror.checked){x.translate(canvas.width,0);x.scale(-1,1)}
  x.drawImage(video,0,0,canvas.width,canvas.height);
  shutterSound();
  canvas.toBlob(async blob=>{
    await addMedia(blob,'image/jpeg','photo-'+Date.now()+'.jpg');
    say('Photo saved');
  },'image/jpeg',.96);
}
async function startRecording(){
  if(!stream)return say('Camera is not ready.');
  chunks=[];
  try{
    let mime='';
    if(window.MediaRecorder){
      const candidates=['video/mp4;codecs=avc1','video/mp4','video/webm;codecs=vp8,opus','video/webm'];
      mime=candidates.find(t=>MediaRecorder.isTypeSupported?.(t))||'';
      rec=mime?new MediaRecorder(stream,{mimeType:mime,videoBitsPerSecond:quality.value==='2160'?12000000:quality.value==='1080'?7000000:3500000})
               :new MediaRecorder(stream);
    }else return say('Video recording is not supported on this device.');
    rec.ondataavailable=e=>{if(e.data&&e.data.size)chunks.push(e.data)};
    rec.onerror=()=>{recording=false;capture.classList.remove('recording');say('Video recording error.')};
    rec.onstop=async()=>{
      const actualType=rec.mimeType||mime||'video/webm';
      const ext=actualType.includes('mp4')?'.mp4':'.webm';
      const blob=new Blob(chunks,{type:actualType});
      chunks=[];
      recording=false;capture.classList.remove('recording');$('statusText').textContent='VIDEO';
      await addMedia(blob,actualType,'video-'+Date.now()+ext,true);
      say('Video saved');
    };
    rec.start(250);
    recording=true;
    capture.classList.add('recording');
    $('statusText').textContent='REC';
  }catch(e){
    recording=false;capture.classList.remove('recording');
    say('Video recording could not start.');
  }
}
function stopRecording(){if(rec&&recording)rec.stop()}
capture.onclick=async()=>{if(mode==='video')recording?stopRecording():startRecording();else await takePhoto()};
document.querySelectorAll('.mode-strip button').forEach(b=>b.onclick=async()=>{
  if(recording)return;
  mode=b.dataset.mode;
  document.querySelectorAll('.mode-strip button').forEach(x=>x.classList.remove('selected'));b.classList.add('selected');
  $('statusText').textContent=mode==='50m'?'50M':mode.toUpperCase();
  if(mode==='50m')fiftyM.checked=true;
  await startCamera();
});
async function openDB(){
  return new Promise((resolve,reject)=>{
    const req=indexedDB.open('CameraAppProDB',1);
    req.onupgradeneeded=()=>req.result.createObjectStore('media',{keyPath:'id',autoIncrement:true});
    req.onsuccess=()=>resolve(req.result);req.onerror=()=>reject(req.error);
  });
}
async function autoSaveToDevice(blob,name){
  try{
    if(window.Android?.saveMedia){
      const b64=await new Promise((resolve,reject)=>{const r=new FileReader();r.onload=()=>resolve(String(r.result).split(',')[1]);r.onerror=reject;r.readAsDataURL(blob)});
      Android.saveMedia(b64,blob.type,name);
      return;
    }
    const url=URL.createObjectURL(blob),a=document.createElement('a');a.href=url;a.download=name;a.style.display='none';document.body.appendChild(a);a.click();a.remove();setTimeout(()=>URL.revokeObjectURL(url),3000);
  }catch(e){}
}
async function addMedia(blob,type,name,autoSave=true){
  const item={blob,type,name,created:Date.now()};
  mediaItems.push(item);selectedIndex=mediaItems.length-1;
  try{await new Promise((res,rej)=>{const tx=db.transaction('media','readwrite');tx.objectStore('media').add(item);tx.oncomplete=res;tx.onerror=()=>rej(tx.error)})}catch(e){}
  updateThumb(blob);if(autoSave)await autoSaveToDevice(blob,name);openGallery(selectedIndex);
}
function updateThumb(blob){thumb.src=URL.createObjectURL(blob);thumb.classList.add('show');galleryEmpty.style.display='none'}
function openGallery(index=mediaItems.length-1){
  if(index<0||!mediaItems.length){$('galleryPlaceholder').hidden=false;$('galleryImage').hidden=true;$('galleryVideo').hidden=true;$('galleryActions').hidden=true}
  else{
    selectedIndex=index;const item=mediaItems[index],url=URL.createObjectURL(item.blob);
    $('galleryPlaceholder').hidden=true;$('galleryActions').hidden=false;
    if(item.type.startsWith('image')){$('galleryImage').src=url;$('galleryImage').hidden=false;$('galleryVideo').hidden=true;$('editImage').style.display='block'}
    else{$('galleryVideo').src=url;$('galleryVideo').hidden=false;$('galleryImage').hidden=true;$('editImage').style.display='none'}
  }
  $('galleryModal').classList.add('show');
}
gallery.onclick=()=>openGallery();
$('closeGallery').onclick=()=>{$('galleryModal').classList.remove('show')};
$('filePicker').onchange=async e=>{const f=e.target.files[0];if(f){await addMedia(f,f.type,f.name)}e.target.value=''};
$('shareMedia').onclick=async()=>{
  const item=mediaItems[selectedIndex];if(!item)return;
  try{
    const file=new File([item.blob],item.name,{type:item.type});
    if(window.Android?.shareMedia){
      const b64=await new Promise((resolve,reject)=>{const r=new FileReader();r.onload=()=>resolve(String(r.result).split(',')[1]);r.onerror=reject;r.readAsDataURL(item.blob)});
      Android.shareMedia(b64,item.type,item.name);
      return;
    }
    if(navigator.share){
      if(navigator.canShare?.({files:[file]})){await navigator.share({files:[file],title:'Camera App Pro'});return}
      await navigator.share({title:item.name,text:'Camera App Pro'});return;
    }
    say('المشاركة غير متاحة');
  }catch(e){}
};
$('deleteMedia').onclick=async()=>{
  if(selectedIndex<0)return;
  const item=mediaItems[selectedIndex];
  if(!confirm('Delete this photo or video?'))return;
  mediaItems.splice(selectedIndex,1);
  try{const tx=db.transaction('media','readwrite');tx.objectStore('media').delete(item.id)}catch(e){}
  if(mediaItems.length){selectedIndex=Math.min(selectedIndex,mediaItems.length-1);openGallery(selectedIndex);updateThumb(mediaItems[selectedIndex].blob)}
  else{$('galleryModal').classList.remove('show');thumb.classList.remove('show');galleryEmpty.style.display='block'}
};
let editState={rotation:0,flip:false};
const editCanvas=$('editorCanvas');
$('editImage').onclick=()=>{const item=mediaItems[selectedIndex];if(!item)return;editState={rotation:0,flip:false};['brightness','contrast','saturation'].forEach(id=>$(id).value=100);$('editorModal').classList.add('show');renderEditor()};
$('closeEditor').onclick=()=>{$('editorModal').classList.remove('show')};
['brightness','contrast','saturation'].forEach(id=>$(id).oninput=renderEditor);
$('rotateLeft').onclick=()=>{editState.rotation=(editState.rotation+270)%360;renderEditor()};
$('flipImage').onclick=()=>{editState.flip=!editState.flip;renderEditor()};
$('resetEdit').onclick=()=>{editState={rotation:0,flip:false};['brightness','contrast','saturation'].forEach(id=>$(id).value=100);renderEditor()};
async function renderEditor(){
  const item=mediaItems[selectedIndex];if(!item)return;
  const img=new Image();img.onload=()=>{
    const rot=editState.rotation%360,swap=rot===90||rot===270;
    editCanvas.width=swap?img.naturalHeight:img.naturalWidth;editCanvas.height=swap?img.naturalWidth:img.naturalHeight;
    const c=editCanvas.getContext('2d');c.save();c.translate(editCanvas.width/2,editCanvas.height/2);c.rotate(rot*Math.PI/180);c.scale(editState.flip?-1:1,1);
    c.filter=`brightness(${$('brightness').value}%) contrast(${$('contrast').value}%) saturate(${$('saturation').value}%)`;
    c.drawImage(img,-img.naturalWidth/2,-img.naturalHeight/2);c.restore();
  };img.src=URL.createObjectURL(item.blob);
}
$('saveEdit').onclick=()=>editCanvas.toBlob(async blob=>{if(!blob)return;await addMedia(blob,'image/jpeg','edited-'+Date.now()+'.jpg');$('editorModal').classList.remove('show');say('Edited photo saved')},'image/jpeg',.96);
$('appInfoBtn').onclick=()=>{$('infoModal').classList.add('show')};
$('closeInfo').onclick=()=>{$('infoModal').classList.remove('show')};
$('infoModal').addEventListener('click',e=>{if(e.target===$('infoModal'))$('infoModal').classList.remove('show')});
async function loadStored(){
  try{
    db=await openDB();
    const rows=await new Promise((res,rej)=>{const r=db.transaction('media','readonly').objectStore('media').getAll();r.onsuccess=()=>res(r.result);r.onerror=()=>rej(r.error)});
    mediaItems=rows.sort((a,b)=>a.created-b.created);
    if(mediaItems.length)updateThumb(mediaItems[mediaItems.length-1].blob);
  }catch(e){db={transaction:()=>{throw new Error('db')}}}
}
window.addEventListener('beforeunload',stopStream);
(async()=>{await loadStored();await startCamera()})();
