import Navi from "@/components/navi/Navi";
import "../styles/globals.css";
import type { AppProps } from "next/app";
import { RecoilRoot } from "recoil";
import { useRouter } from "next/router";
import { ToastContainer } from "react-toastify";
import "react-toastify/dist/ReactToastify.css";
import { QueryClient, QueryClientProvider } from "react-query";

export default function App({ Component, pageProps }: AppProps) {
  const router = useRouter();
  const isChattingRoom = router.pathname.includes("/chatting/room/");
  const queryClient = new QueryClient();

  return (
    <QueryClientProvider client={queryClient}>
      <ToastContainer />
      <RecoilRoot>
        <ToastContainer position="top-center" />
        {isChattingRoom ? (
          <Component {...pageProps} />
        ) : (
          <>
            <Component {...pageProps} />
            <Navi />
          </>
        )}
      </RecoilRoot>
    </QueryClientProvider>
  );
}
