"""Icona dell'app: la chiave a puntini (dalle carte di Mattia Giordano) su fondo nero.
Uso: python3 tools/icona_app.py  → app/src/main/res/drawable/ic_launcher_fg.xml"""
import os
from PIL import Image
R = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
g = Image.open(f"{R}/app/src/main/assets/icone/chiave.png").convert("L")
pts = [(x, y) for y in range(25) for x in range(25) if g.getpixel((x, y)) > 100]
xs = [p[0] for p in pts]; ys = [p[1] for p in pts]
cx = (min(xs) + max(xs)) / 2; cy = (min(ys) + max(ys)) / 2
pitch, r = 4.3, 1.95       # chiave grande (quasi tutta l icona), puntini fitti
d = "".join(f"M{54+(x-cx)*pitch-r:.2f},{54+(y-cy)*pitch:.2f}a{r:.2f},{r:.2f} 0,1 1,{2*r:.2f},0a{r:.2f},{r:.2f} 0,1 1,-{2*r:.2f},0" for x, y in pts)
open(f"{R}/app/src/main/res/drawable/ic_launcher_fg.xml", "w").write(
    '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
    '    android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">\n'
    f'  <path android:fillColor="#FFFFFF" android:pathData="{d}"/>\n</vector>\n')
print("ok,", len(pts), "punti")
