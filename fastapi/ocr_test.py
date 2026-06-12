from services.ocr_services import extract_text_from_image_bytes

def main():
    image_path = "services/book.jpg"

    with open(image_path, "rb") as f:
        image_bytes = f.read()

    text = extract_text_from_image_bytes(image_bytes)

    print("OCR 결과:", text)

if __name__ == "__main__":
    main()
