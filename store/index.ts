import {combineReducers, configureStore} from '@reduxjs/toolkit';
import userReducer from './user.slice';

const rootReducer = combineReducers({
  user: userReducer,
});

const store = configureStore({
  reducer: rootReducer,
  devTools: process.env.NODE_ENV !== 'production',
  //   middleware: (getDefaultMiddleware) =>
  //     getDefaultMiddleware().concat(
  //       auditsApi.middleware,
  //       supportTicketsApi.middleware,
  //       userApi.middleware
  //     ),
});

export type RootState = ReturnType<typeof rootReducer>;
export type AppDispatch = typeof store.dispatch;

export default store;
