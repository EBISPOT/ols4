export default function LoadingOverlay({ message }: { message?: string }) {
  return (
    <div className="fixed top-0 left-0 w-full h-full z-50 backdrop-blur-sm">
      <div className="absolute top-1/2 left-1/2 ">
        <div className="spinner-default w-20 h-20"></div>
        <div className="text-neutral-black text-center font-bold ">{message}</div>
      </div>
    </div>
  );
}
