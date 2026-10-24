import zipfile,struct,sys
src,html,out=sys.argv[1:4]
z=zipfile.ZipFile(src); o=zipfile.ZipFile(out,'w')
for zi in z.infolist():
    n=zi.filename
    if n.startswith('META-INF/'): continue
    data=open(html,'rb').read() if n=='assets/index.html' else z.read(n)
    st=zi.compress_type==0
    ni=zipfile.ZipInfo(n,date_time=(1980,1,1,0,0,0)); ni.compress_type=zipfile.ZIP_STORED if st else zipfile.ZIP_DEFLATED
    if st:
        pad=(-(o.fp.tell()+30+len(n.encode())))%4
        if pad: pad+=4 if pad<4 else 0; ni.extra=struct.pack('<HH',0xD935,pad-4)+b'\0'*(pad-4)
    o.writestr(ni,data)
o.close()
for zi in zipfile.ZipFile(out).infolist():
    if zi.compress_type==0: assert (zi.header_offset+30+len(zi.filename.encode())+len(zi.extra))%4==0, zi.filename
print('packed',out)
