from fastapi import APIRouter, Depends, status, HTTPException
from sqlalchemy.ext.asyncio import AsyncSession
from typing import Annotated
from database import get_db_async
import schemas, models
from services import user_service
from auth import get_current_user_id, get_current_active_user

router = APIRouter(
    prefix="/users",
    tags=["Users"]
)

@router.post("/", response_model=schemas.User, status_code=status.HTTP_201_CREATED)
async def register_user(
    db: Annotated[AsyncSession, Depends(get_db_async)],
    user_data: schemas.UserCreate,
    firebase_uid: Annotated[str, Depends(get_current_user_id)]
):
    if await user_service.get_user_by_id(db, firebase_uid):
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail="User already registered."
        )
    new_user = await user_service.create_user(db, user_data, firebase_uid)
    return new_user

@router.get("/me", response_model=schemas.User)
async def read_users_me(
    current_user: Annotated[schemas.User, Depends(get_current_active_user)]
):
    return current_user
