from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select, insert
from typing import Optional
from models import User
from schemas import UserCreate

async def get_user_by_id(db: AsyncSession, user_id: str) -> Optional[User]:
    stmt = select(User).filter(User.id == user_id)
    result = await db.execute(stmt)
    return result.scalars().first()

async def create_user(
    db: AsyncSession,
    user_data: UserCreate,
    firebase_uid: str
) -> User:

    db_user = User(
        id=firebase_uid,
        email=user_data.email,
        nickname=user_data.nickname
    )

    db.add(db_user)
    await db.commit()
    await db.refresh(db_user)
    return db_user
