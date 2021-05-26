package com.zhw.pdf;

import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.io.source.PdfTokenizer;
import com.itextpdf.io.source.RandomAccessFileOrArray;
import com.itextpdf.io.source.RandomAccessSourceFactory;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.*;
import com.itextpdf.kernel.pdf.annot.PdfAnnotation;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import com.itextpdf.kernel.pdf.canvas.parser.PdfCanvasProcessor;
import com.itextpdf.kernel.pdf.canvas.parser.PdfDocumentContentParser;
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor;
import com.itextpdf.kernel.pdf.canvas.parser.listener.IPdfTextLocation;
import com.itextpdf.kernel.pdf.canvas.parser.listener.RegexBasedLocationExtractionStrategy;
import com.itextpdf.kernel.pdf.canvas.parser.util.PdfCanvasParser;
import com.itextpdf.kernel.pdf.xobject.PdfImageXObject;
import com.itextpdf.layout.element.Image;
import com.itextpdf.pdfcleanup.PdfCleanUpLocation;
import com.itextpdf.pdfcleanup.PdfCleanUpTool;


import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

/**
 * Hello world!
 *
 */
public class App 
{
    private static final String src = "/Users/hongweizou/Downloads/pdf/src/";
    private static final String desc = "/Users/hongweizou/Downloads/pdf/desc/";

    public static void main( String[] args ) throws Exception {
        System.out.println("Hello World!");

        String desc = "/Users/hongweizou/Downloads/test.pdf";
        //String src = "/Users/hongweizou/Downloads/GUIDE_354a90d2d8.pdf";
        //String src = "/Users/hongweizou/Downloads/GUIDE_58556eb183.pdf";
        //String src = "/Users/hongweizou/Downloads/【医脉通】女性抗栓治疗的中国专家建议.pdf";
        String src = "/Users/hongweizou/Downloads/【医脉通】糖尿病患者血糖波动管理专家共识.pdf";
        //String src = "/Users/hongweizou/Downloads/test3.pdf";

        //manipulatePdf(src, desc);
        removeAll();
    }

    public static void removeAll() throws IOException {
        Files.list(Paths.get(src)).forEach(file -> {
            String descFile = desc + file.getFileName().toString();
            System.out.println(descFile);
            try {
                manipulatePdf(file.toString(), descFile);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public static List<PdfCleanUpLocation> getRectangle(PdfDocument pdfDoc) {
        List<PdfCleanUpLocation> cleanUpLocations = new ArrayList<>();
        for (int i = 1; i <= pdfDoc.getNumberOfPages(); i++) {
            PdfPage page = pdfDoc.getPage(i);
            RegexBasedLocationExtractionStrategy strategy = new RegexBasedLocationExtractionStrategy("http://guide.medlive.cn/");
            PdfCanvasProcessor canvasProcessor = new PdfCanvasProcessor(strategy);
            canvasProcessor.processPageContent(page);
            Collection<IPdfTextLocation> resultantLocations = strategy.getResultantLocations();

            for (IPdfTextLocation location : resultantLocations) {
                System.out.println("location " + location.getRectangle().toString());
                cleanUpLocations.add(new PdfCleanUpLocation(i, new Rectangle(location.getRectangle().getX() - 1
                        , location.getRectangle().getY() - 1
                        , location.getRectangle().getWidth() + 1
                        , location.getRectangle().getHeight() + 1),
                        ColorConstants.WHITE));
            }
        }

        return cleanUpLocations;
    }

    public static void removeText(PdfDocument pdfDoc, List<PdfCleanUpLocation> cleanUpLocations) throws IOException {
        PdfCleanUpTool cleaner = new PdfCleanUpTool(pdfDoc, cleanUpLocations);
        cleaner.setProcessAnnotations(true);
        cleaner.cleanUp();
    }

    public static void removeAnnots(PdfDictionary page) {
        PdfArray annots = page.getAsArray(PdfName.Annots);
        if (annots == null) {
            return;
        }

        Iterator<PdfObject> iterator = annots.iterator();
        while (iterator.hasNext()) {
            PdfObject pdfObject = iterator.next();
            if (pdfObject.isDictionary()) {
                PdfDictionary next = (PdfDictionary) pdfObject;
                PdfDictionary uriDic = next.getAsDictionary(PdfName.A);
                if (uriDic != null) {
                    String uri = uriDic.getAsString(PdfName.URI).getValue();
                    System.out.println(uri);
                    if (uri == null || uri.contains("medlive") || uri.trim().equals("")) {
                        System.out.println("delete link " + uri);
                        iterator.remove();
                    }
                }
            }
        }
    }

    /**
     * 获取高度小于60、宽度小于200，且数量大于页数一半的图片名字
     * @param pdfDoc
     * @return
     */
    public static List<String> getImageName(PdfDocument pdfDoc) {
        Map<String, Integer> imageMap = new HashMap<>();
        List<String> list = new ArrayList<>();
        int pageCount = pdfDoc.getNumberOfPages();

        for (int i = 1; i <= pdfDoc.getNumberOfPages(); i++) {
            PdfDictionary page = pdfDoc.getPage(i).getPdfObject();
            PdfDictionary resources = page.getAsDictionary(PdfName.Resources);
            PdfDictionary xobjects = resources.getAsDictionary(PdfName.XObject);
            Iterator<Map.Entry<PdfName, PdfObject>> it = xobjects.entrySet().iterator();

            while (it.hasNext()) {
                Map.Entry<PdfName, PdfObject> next = it.next();
                if (next.getValue() != null && next.getValue().isStream()) {
                    PdfStream stream = (PdfStream) next.getValue();
                    String type = stream.getAsName(PdfName.Subtype).getValue();
                    if ("Image".equals(type)) {
                        double width = stream.getAsNumber(PdfName.Width).getValue();
                        double height = stream.getAsNumber(PdfName.Height).getValue();
                        System.out.println("name " + next.getKey().getValue());
                        System.out.println("width " + width);
                        System.out.println("hight " + height);
                        //if (width < 200 && height < 60) {
                            imageMap.compute(next.getKey().getValue(), (key, value) -> value == null ? 1 : ++value);
                        //}
                    }
                }
            }
        }

        System.out.println(imageMap);
        imageMap.forEach((k, v) -> {
            if (2 * v > pageCount) {
                list.add(k);
            }
        });
        System.out.println(list);

        return list;
    }

    /**
     * 删除每页最后一张图片
     * @param page
     */
    public static void removeImage(List<String> imageList, PdfDictionary page) {
        PdfDictionary resources = page.getAsDictionary(PdfName.Resources);
        PdfDictionary xobjects = resources.getAsDictionary(PdfName.XObject);
        Iterator<Map.Entry<PdfName, PdfObject>> it = xobjects.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<PdfName, PdfObject> next = it.next();
            if (imageList.contains(next.getKey().getValue())) {
                System.out.println("delete image " + next.getKey().getValue());
                it.remove();
            }
        }

    }

    public static void manipulatePdf(String src, String dest) throws Exception {
        PdfDocument pdfDoc = new PdfDocument(new PdfReader(src), new PdfWriter(dest));
        List<String> imageList = getImageName(pdfDoc);
        List<PdfCleanUpLocation> rectangleList = getRectangle(pdfDoc);
        for (int i = 1; i <= pdfDoc.getNumberOfPages(); i++) {
            PdfDictionary page = pdfDoc.getPage(i).getPdfObject();
            removeImage(imageList, page);
            removeAnnots(page);
        }

        removeText(pdfDoc, rectangleList);
        pdfDoc.close();
    }

}
