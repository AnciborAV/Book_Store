package ru.aav.about;

import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.*;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Bounds encoded and decoded size, normalizes orientation, strips metadata, preserves aspect ratio. */
@Component
public class AboutImages {
    public byte[] process(MultipartFile file) {
        if(file.isEmpty() || file.getSize()>5*1024*1024) throw invalid();
        try {
            byte[] bytes=file.getBytes();
            try(var input=ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
                var readers=ImageIO.getImageReaders(input);if(!readers.hasNext()) throw invalid();
                var reader=readers.next();
                try {
                    reader.setInput(input);String format=reader.getFormatName();
                    if(!format.equalsIgnoreCase("png") && !format.equalsIgnoreCase("jpeg")) throw invalid();
                    int w=reader.getWidth(0),h=reader.getHeight(0);
                    if(w<1 || h<1 || w>6000 || h>6000 || (long)w*h>20000000) throw invalid();
                    var source=reader.read(0);int orientation=orientation(bytes);
                    int ow=orientation>=5?h:w,oh=orientation>=5?w:h;
                    double scale=Math.min(1,1600d/Math.max(ow,oh));
                    var result=new BufferedImage(Math.max(1,(int)Math.round(ow*scale)),Math.max(1,(int)Math.round(oh*scale)),BufferedImage.TYPE_INT_ARGB);
                    var graphics=result.createGraphics();
                    try {
                        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                        graphics.scale((double)result.getWidth()/ow,(double)result.getHeight()/oh);
                        var transform=switch(orientation) {
                            case 2 -> new AffineTransform(-1,0,0,1,w,0);
                            case 3 -> new AffineTransform(-1,0,0,-1,w,h);
                            case 4 -> new AffineTransform(1,0,0,-1,0,h);
                            case 5 -> new AffineTransform(0,1,1,0,0,0);
                            case 6 -> new AffineTransform(0,1,-1,0,h,0);
                            case 7 -> new AffineTransform(0,-1,-1,0,h,w);
                            case 8 -> new AffineTransform(0,-1,1,0,0,w);
                            default -> new AffineTransform();
                        };
                        graphics.transform(transform);graphics.drawImage(source,0,0,null);
                    } finally {graphics.dispose();}
                    var out=new ByteArrayOutputStream();ImageIO.write(result,"png",out);
                    if(out.size()>8*1024*1024) throw invalid();
                    return out.toByteArray();
                } finally {reader.dispose();}
            }
        } catch(IOException | IllegalArgumentException ex) {throw invalid();}
    }
    private int orientation(byte[] bytes) {
        // JPEG APP1 Exif orientation (TIFF tag 0x0112). Ignore absent/malformed metadata.
        try {
            if(bytes.length<4 || (bytes[0]&255)!=255 || (bytes[1]&255)!=216) return 1;
            int p=2;
            while(p+4<=bytes.length && (bytes[p]&255)==255) {
                int marker=bytes[p+1]&255;if(marker==218 || marker==217) break;
                int length=((bytes[p+2]&255)<<8)|(bytes[p+3]&255);
                if(length<2 || p+2L+length>bytes.length) break;
                if(marker==225 && length>=16 && bytes[p+4]=='E' && bytes[p+5]=='x' && bytes[p+6]=='i' && bytes[p+7]=='f' && bytes[p+8]==0 && bytes[p+9]==0) {
                    var t=ByteBuffer.wrap(bytes,p+10,length-8).slice();
                    short order=t.getShort(0);if(order!=0x4949 && order!=0x4d4d) return 1;
                    t.order(order==0x4949?ByteOrder.LITTLE_ENDIAN:ByteOrder.BIG_ENDIAN);
                    if(t.getShort(2)!=42) return 1;
                    long start=Integer.toUnsignedLong(t.getInt(4));if(start+2>t.limit()) return 1;
                    int count=Short.toUnsignedInt(t.getShort((int)start));
                    for(int n=0;n<count;n++) {
                        long offset=start+2L+12L*n;if(offset+12>t.limit()) return 1;int i=(int)offset;
                        if(Short.toUnsignedInt(t.getShort(i))==0x112 && t.getShort(i+2)==3 && t.getInt(i+4)==1) {
                            int value=Short.toUnsignedInt(t.getShort(i+8));return value>=1 && value<=8?value:1;
                        }
                    }
                }
                p+=2+length;
            }
        } catch(IndexOutOfBoundsException ignored) {}
        return 1;
    }
    private ResponseStatusException invalid() {return new ResponseStatusException(HttpStatus.BAD_REQUEST,"Нужна фотография PNG или JPEG до 5 МБ, не более 6000×6000 и 20 млн пикселей");}
}
