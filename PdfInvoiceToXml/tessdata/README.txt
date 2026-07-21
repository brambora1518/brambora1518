Do této složky stáhněte trénovací data pro OCR (Tesseract):

  ces.traineddata  -  https://raw.githubusercontent.com/tesseract-ocr/tessdata_fast/main/ces.traineddata

(volitelně i eng.traineddata ze stejného repozitáře, pokud by faktury
obsahovaly anglický text)

Soubor uložte přímo sem, do PdfInvoiceToXml/tessdata/ces.traineddata.
Projekt je nastavený tak, že ho při buildu zkopíruje vedle .exe.

Bez tohoto souboru OCR (rozpoznávání textu z naskenovaných/obrázkových
PDF faktur) nebude fungovat - běžné textové PDF (s vrstvou textu)
funguje i bez OCR.
