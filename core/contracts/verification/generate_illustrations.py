"""Original, reproducible Android vector artwork. No external media or imaging packages."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]

def path(data, colour, stroke=None):
    extra = f' android:strokeColor="{stroke}" android:strokeWidth="3"' if stroke else ''
    return f'<path android:pathData="{data}" android:fillColor="{colour}"{extra}/>'

def rect(x, y, w, h, colour):
    return path(f'M{x},{y}h{w}v{h}h{-w}z', colour)

def oval(x, y, rx, ry, colour):
    return path(f'M{x-rx},{y}a{rx},{ry} 0,1 0,{2*rx},0a{rx},{ry} 0,1 0,{-2*rx},0', colour)

def house(x, y, width=65, height=70, colour='#E8BB66'):
    return [rect(x,y,width,height,colour), path(f'M{x-8},{y}L{x+width/2},{y-25}L{x+width+8},{y}z','#654D45'),
            rect(x+width*.42,y+height*.48,width*.2,height*.52,'#36585C'),
            rect(x+8,y+16,15,18,'#FFF1CE')]

def vector(parts):
    return '<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="320dp" android:height="200dp" android:viewportWidth="320" android:viewportHeight="200">\n' + '\n'.join(parts) + '\n</vector>\n'

def write(module, name, parts):
    target = ROOT / 'feature' / module / 'src/main/res/drawable' / f'{module}_{name}.xml'
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(vector(parts), encoding='utf-8')

def main():
    sky = [rect(0,0,320,200,'#D9ECE8'), oval(266,38,19,19,'#F1C775')]
    riverside = sky + rects((0,140,320,60,'#62A7AE'),(0,132,320,8,'#D8C5A8'))
    for x in (15,95,175): riverside += house(x,68)
    riverside += [path('M15,165h80M135,181h125M222,155h74','#00000000','#C8E5DE')]
    write('gallery','riverside',riverside)
    lanterns = [rect(0,0,320,200,'#283D57'),path('M0,25Q160,90 320,25','#00000000','#E0C68D')]
    for x,y,c in ((35,45,'#D16F56'),(95,64,'#EDBD6D'),(160,71,'#CB6C69'),(225,64,'#F0BD66'),(290,45,'#D77F59')):
        lanterns += [rect(x-1,y-10,2,22,'#E0C68D'),oval(x,y+15,17,23,c),rect(x-7,y+38,14,4,'#D8B376'),rect(x-1,y+42,2,12,'#E0C68D')]
    lanterns += [path('M0,200L45,140H275L320,200z','#547175')]
    write('gallery','lanterns',lanterns)
    write('gallery','bridge',sky+[rect(0,150,320,50,'#6AA5AD'),rect(38,100,244,35,'#B57751'),path('M25,100L65,65H255L295,100z','#654D45'),rect(65,100,10,51,'#654D45'),rect(245,100,10,51,'#654D45'),path('M115,150Q160,112 205,150z','#6AA5AD'),rect(80,108,22,18,'#F1D9AE'),rect(216,108,22,18,'#F1D9AE')])
    write('gallery','yellow_wall',sky+house(25,70,270,130)+[rect(123,116,60,84,'#38585C'),path('M70,200L110,70H145L110,200z','#D9A45A')])
    write('gallery','balcony',[rect(0,0,320,200,'#E8BC6D'),rect(90,10,140,100,'#496568'),rect(65,100,190,12,'#775745'),path('M75,112v40M110,112v40M145,112v40M180,112v40M215,112v40M245,112v40M65,152h190','#00000000','#775745'),oval(55,153,22,32,'#659477'),rect(42,173,26,27,'#B27352')])
    market=sky+[rect(0,165,320,35,'#C8B99D')]
    for x,c in ((15,'#D88165'),(115,'#7CA49A'),(215,'#D8AD64')):
        market += [rect(x,92,90,72,'#B58D6A'),path(f'M{x-6},100L{x+12},60H{x+78}L{x+96},100z',c),rect(x+8,115,74,38,'#F0D5A5')]
        for k in range(3):market += [oval(x+21+k*22,132,9,9,'#D68E4B')]
    write('gallery','market',market)
    write('gallery','ricefield',sky+[path('M0,95Q90,65 180,104T320,90V200H0z','#7CA578'),path('M0,135Q95,95 220,135T320,125V200H0z','#A8BD70'),path('M125,200L190,100H207L173,200z','#D9C6A0'),path('M0,175L95,141M225,171L320,142M15,120L75,101','#00000000','#D5D895')])
    write('gallery','beach',sky+[rect(0,95,320,105,'#61A7B2'),path('M0,142Q110,125 320,168V200H0z','#EFD6A6'),path('M0,135Q120,119 320,160','#00000000','#F5EFE0'),path('M235,155L250,110M212,118Q250,80 285,118z','#397F77')])
    write('gallery','alley',sky+[path('M0,0H100L127,200H0z','#D9A65F'),path('M320,0H220L193,200H320z','#EAC77D'),path('M127,200L151,85H170L193,200z','#B7A98D'),rect(25,45,25,50,'#537374'),rect(267,80,25,50,'#537374')])
    write('gallery','boat',sky+[rect(0,90,320,110,'#68A3AA'),path('M55,136H260L224,163H88z','#73584B'),path('M102,136L122,110H204L229,136z','#E3B96F'),rect(161,58,4,78,'#73584B'),path('M167,60L217,111H167z','#F2E4BC'),path('M45,176h65M145,185h95M245,167h50','#00000000','#C1E0D9')])
    write('gallery','roof',[rect(0,0,320,200,'#DCE8DF'),path('M0,115L160,40L320,115z','#725448'),path('M0,145L160,70L320,145z','#9E7255'),path('M0,175L160,100L320,175z','#B18967'),path('M30,175L160,112L290,175M55,175L160,124L265,175M80,175L160,136L240,175','#00000000','#775443')])
    write('gallery','courtyard',[rect(0,0,320,200,'#F1D89F'),rect(35,35,250,165,'#B7CAB0'),rect(65,55,190,145,'#E1D3AF'),oval(160,130,37,17,'#87A8A1'),rect(237,100,18,78,'#94714C'),oval(246,65,43,48,'#6B9475'),rect(55,130,35,70,'#BA8562'),oval(72,115,28,36,'#7C9D78')])
    for name in ('cao_lau','white_rose','chicken_rice','banh_mi'):
        parts=[rect(0,0,320,200,'#EFE5D1'),oval(160,112,126,65,'#FCF8EC'),oval(160,112,107,51,'#D3D9C9')]
        if name=='cao_lau':
            parts += [oval(160,113,91,40,'#B89B63')]
            for y in range(95,133,8):parts += [path(f'M91,{y}Q145,{y-20} 219,{y}','#00000000','#E4CB82')]
            parts += [oval(207,96,26,13,'#9A6451'),oval(114,95,16,20,'#698C5E')]
        elif name=='white_rose':
            for x,y in ((105,99),(159,85),(211,105),(139,134),(193,136)):
                parts += [path(f'M{x-23},{y}Q{x},{y-40} {x+23},{y}Q{x},{y+26} {x-23},{y}z','#FAF4E4'),oval(x,y-1,5,5,'#D7AA8B')]
        elif name=='chicken_rice':
            parts += [oval(137,109,57,34,'#E5BE5F')]
            for x,y in ((180,100),(198,111),(186,127)):parts += [oval(x,y,32,10,'#EAD6A9')]
            parts += [oval(95,127,17,14,'#73996A'),oval(219,82,15,11,'#C46B51')]
        else:
            parts += [path('M70,140Q45,92 116,67L241,79Q274,120 222,151z','#D5A05C'),path('M78,123L232,98L240,118L91,144z','#689769'),path('M91,120L230,99L224,119L99,139z','#AA7155'),path('M118,79l20,19M164,74l20,19M207,77l20,19','#00000000','#F1D19B')]
        write('food',name,parts)
    print('Generated 12 gallery and 4 food vector illustrations in their feature modules.')

def rects(*items):
    return [rect(*item) for item in items]

if __name__ == '__main__':
    main()
