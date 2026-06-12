import os
import json
import base64
from dotenv import load_dotenv
from openai import AsyncOpenAI

load_dotenv()

client = AsyncOpenAI(api_key=os.getenv("OPENAI_API_KEY"))
MODEL = os.getenv("OPENAI_MODEL", "gpt-5-mini")


async def extract_drug_name_from_image(image_bytes: bytes, mime_type: str = "image/jpeg") -> str | None:
    image_base64 = base64.b64encode(image_bytes).decode("utf-8")
    image_url = f"data:{mime_type};base64,{image_base64}"

    response = await client.responses.create(
        model=MODEL,
        input=[
            {
                "role": "user",
                "content": [
                    {
                        "type": "input_text",
                        "text": """
이 이미지는 약 포장지 또는 약 사진입니다.
이미지에서 약 이름만 찾아주세요.

규칙:
- 약 이름만 JSON으로 답하세요.
- 추측하지 마세요.
- 약 이름을 못 찾으면 drug_name을 null로 주세요.
- 설명 문장은 쓰지 마세요.

응답 형식:
{"drug_name": "약 이름"}
"""
                    },
                    {
                        "type": "input_image",
                        "image_url": image_url
                    }
                ]
            }
        ],
    )

    try:
        data = json.loads(response.output_text)
        return data.get("drug_name")
    except Exception:
        return None
