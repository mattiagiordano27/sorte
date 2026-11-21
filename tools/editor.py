"""Crea l'EDITOR DELLE ICONE (tools/editor_icone.html) con dentro le icone attuali dell'oracolo aperto.
Uso: python3 tools/editor.py && open tools/editor_icone.html
Nell'editor: clic/trascina per accendere o spegnere i punti, poi "Salva PNG": il file finisce in Download.
Rimetti i PNG in app/src/main/assets/icone/."""
import base64, glob, json, os

HERE = os.path.dirname(os.path.abspath(__file__))
ICONS = os.path.join(os.path.dirname(HERE), "app", "src", "main", "assets", "icone")
icons = {}
for p in sorted(glob.glob(os.path.join(ICONS, "*.png"))):
    icons[os.path.basename(p)[:-4]] = base64.b64encode(open(p, "rb").read()).decode()

html = r"""<!doctype html><html lang="it"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Editor icone</title>
<style>
:root{--bg:#000;--fg:#f2f2f2;--muted:#8a8a8a;--line:#2a2a2a;--red:#d71921}
*{box-sizing:border-box}
body{background:var(--bg);color:var(--fg);font-family:ui-monospace,Menlo,monospace;margin:0;padding:24px 16px}
h1{font-size:13px;letter-spacing:3px;color:var(--red);font-weight:400;margin:0 0 4px}
p{color:var(--muted);font-size:12px;line-height:1.6;margin:0 0 20px;max-width:720px}
.wrap{display:flex;gap:28px;flex-wrap:wrap;align-items:flex-start}
.list{display:grid;grid-template-columns:repeat(3,96px);gap:10px}
.thumb{border:1px solid var(--line);border-radius:14px;padding:8px;cursor:pointer;text-align:center;font-size:10px;letter-spacing:1px;color:var(--muted)}
.thumb.on{border-color:var(--fg);color:var(--fg)}
.thumb canvas{width:78px;height:78px;display:block;margin:0 auto 6px}
#ed{width:min(560px,92vw);height:min(560px,92vw);touch-action:none;cursor:crosshair;display:block}
.bar{display:flex;flex-wrap:wrap;gap:8px;margin-top:14px;align-items:center}
.chip{border:1px solid var(--line);border-radius:99px;padding:8px 14px;font-size:11px;letter-spacing:2px;cursor:pointer;background:none;color:var(--fg);font-family:inherit}
.chip.on{background:var(--fg);color:#000;border-color:var(--fg)}
.save{background:var(--red);border-color:var(--red);color:#fff}
input{background:none;border:1px solid var(--line);border-radius:99px;color:var(--fg);padding:8px 14px;font:inherit;font-size:12px;width:200px}
.name{font-size:20px;letter-spacing:2px;margin-bottom:10px}
</style></head><body>
<h1>EDITOR · ICONE DELL'ORACOLO APERTO</h1>
<p>Scegli un'icona a sinistra. Clic o trascina sulla matrice per disegnare. Tre luci: pieno, tenue (per sfumature), spento.
"Salva PNG" scarica il file in Download con lo stesso nome: rimettilo in <b>app/src/main/assets/icone/</b> (o dimmelo e lo faccio io).
I punti fuori dal cerchio non si vedono sulla Glyph Matrix.</p>
<div class="wrap">
  <div class="list" id="list"></div>
  <div>
    <div class="name" id="nm">—</div>
    <canvas id="ed" width="750" height="750"></canvas>
    <div class="bar">
      <button class="chip on" data-v="255">PIENO</button>
      <button class="chip" data-v="110">TENUE</button>
      <button class="chip" data-v="0">GOMMA</button>
      <span style="width:12px"></span>
      <button class="chip" id="clr">SVUOTA</button>
      <button class="chip" id="undo">ANNULLA</button>
      <button class="chip save" id="save">SALVA PNG</button>
    </div>
    <div class="bar"><input id="newname" placeholder="nome nuova icona"><button class="chip" id="new">NUOVA</button></div>
  </div>
</div>
<script>
const N=25, inC=(x,y)=>(x-12)**2+(y-12)**2<=156;
const ICONS=__ICONS__;
const data={}; let cur=null, val=255, hist=[];
const ed=document.getElementById('ed'), g=ed.getContext('2d');
function load(name,b64){ return new Promise(r=>{ const im=new Image(); im.onload=()=>{ const c=document.createElement('canvas'); c.width=N;c.height=N;
  const x=c.getContext('2d'); x.drawImage(im,0,0,N,N); const d=x.getImageData(0,0,N,N).data; const a=new Uint8Array(N*N);
  for(let i=0;i<N*N;i++) a[i]=Math.round((d[i*4]*0.299+d[i*4+1]*0.587+d[i*4+2]*0.114)*d[i*4+3]/255); data[name]=a; r(); }; im.src='data:image/png;base64,'+b64; }); }
function paint(cv,a,cell,gap){ const x=cv.getContext('2d'); x.fillStyle='#000'; x.fillRect(0,0,cv.width,cv.height);
  for(let y=0;y<N;y++)for(let i=0;i<N;i++){ if(!inC(i,y))continue; const v=a[y*N+i]; const l=Math.round(28+v*227/255); x.fillStyle=`rgb(${l},${l},${l})`; x.fillRect(i*cell+gap,y*cell+gap,cell-2*gap,cell-2*gap);} }
function thumbs(){ const L=document.getElementById('list'); L.innerHTML='';
  Object.keys(data).sort().forEach(n=>{ const t=document.createElement('div'); t.className='thumb'+(n===cur?' on':''); const c=document.createElement('canvas'); c.width=100;c.height=100;
    paint(c,data[n],4,0.5); t.appendChild(c); t.appendChild(document.createTextNode(n.replace('_',' '))); t.onclick=()=>select(n); L.appendChild(t); }); }
function draw(){ if(cur) paint(ed,data[cur],30,2); }
function select(n){ cur=n; hist=[]; document.getElementById('nm').textContent=n.replace('_',' '); draw(); thumbs(); }
let down=false;
function at(e){ const r=ed.getBoundingClientRect(); const x=Math.floor((e.clientX-r.left)/r.width*N), y=Math.floor((e.clientY-r.top)/r.height*N); return [x,y]; }
function set(e){ const [x,y]=at(e); if(x<0||y<0||x>=N||y>=N||!inC(x,y)) return; const a=data[cur]; if(a[y*N+x]!==val){ a[y*N+x]=val; draw(); } }
ed.addEventListener('pointerdown',e=>{ if(!cur)return; hist.push(new Uint8Array(data[cur])); down=true; ed.setPointerCapture(e.pointerId); set(e); });
ed.addEventListener('pointermove',e=>{ if(down) set(e); });
ed.addEventListener('pointerup',()=>{ down=false; thumbs(); });
document.querySelectorAll('[data-v]').forEach(b=>b.onclick=()=>{ val=+b.dataset.v; document.querySelectorAll('[data-v]').forEach(c=>c.classList.remove('on')); b.classList.add('on'); });
document.getElementById('clr').onclick=()=>{ if(!cur)return; hist.push(new Uint8Array(data[cur])); data[cur].fill(0); draw(); thumbs(); };
document.getElementById('undo').onclick=()=>{ if(hist.length){ data[cur]=hist.pop(); draw(); thumbs(); } };
document.getElementById('new').onclick=()=>{ const n=document.getElementById('newname').value.trim().toLowerCase().replace(/\s+/g,'_'); if(!n)return; data[n]=new Uint8Array(N*N); select(n); };
document.getElementById('save').onclick=()=>{ if(!cur)return; const c=document.createElement('canvas'); c.width=N;c.height=N; const x=c.getContext('2d'); const im=x.createImageData(N,N);
  for(let i=0;i<N*N;i++){ const v=data[cur][i]; im.data[i*4]=v;im.data[i*4+1]=v;im.data[i*4+2]=v;im.data[i*4+3]=255; } x.putImageData(im,0,0);
  const a=document.createElement('a'); a.download=cur+'.png'; a.href=c.toDataURL('image/png'); a.click(); };
Promise.all(Object.entries(ICONS).map(([n,b])=>load(n,b))).then(()=>{ const first=Object.keys(data).sort()[0]; select(first); });
</script></body></html>"""
out = os.path.join(HERE, "editor_icone.html")
open(out, "w").write(html.replace("__ICONS__", json.dumps(icons)))
print("ok:", out)
