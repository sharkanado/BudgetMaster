import {createSlice} from '@reduxjs/toolkit';

interface IUserState {
  user: null | string;
}

const initialState = {
  user: null,
};

// This slice is used to store the current authenticated user object
const userSlice = createSlice({
  name: 'user',
  initialState: initialState as IUserState,
  reducers: {
    setUser(state, action: {payload: string}) {
      state.user = action.payload;
    },
    clearUser(state) {
      state.user = null;
    },
  },
});

export const {setUser, clearUser} = userSlice.actions;

export default userSlice.reducer;
