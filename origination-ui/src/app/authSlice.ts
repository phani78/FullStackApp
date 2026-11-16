import { createSlice, type PayloadAction } from "@reduxjs/toolkit";
import type { Role } from "../types";

interface AuthState {
  subject: string;
  role: Role | null;
}

let accessToken: string | null = null;

export function setAccessToken(token: string) {
  accessToken = token;
}

export function getAccessToken() {
  return accessToken;
}

export function clearAccessToken() {
  accessToken = null;
}

const initialState: AuthState = {
  subject: "",
  role: null,
};

const authSlice = createSlice({
  name: "auth",
  initialState,
  reducers: {
    setSession(state, action: PayloadAction<{ subject: string; role: Role }>) {
      state.subject = action.payload.subject;
      state.role = action.payload.role;
    },
    clearSession(state) {
      state.subject = "";
      state.role = null;
    },
  },
});

export const { setSession, clearSession } = authSlice.actions;
export default authSlice.reducer;
