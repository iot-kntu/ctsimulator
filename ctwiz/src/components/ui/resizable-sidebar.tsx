import * as React from "react";

import { cn } from "@/lib/utils";

interface ResizableSidebarProps extends React.HTMLAttributes<HTMLDivElement> {
    side: "left" | "right";
    width: number;
    onResize: (width: number) => void;
    isOpen: boolean;
    minWidth?: number;
    maxWidth?: number;
}

export function ResizableSidebar({
    side,
    width,
    onResize,
    isOpen,
    minWidth = 200,
    maxWidth = 600,
    className,
    children,
    ...props
}: ResizableSidebarProps) {
    const [isResizing, setIsResizing] = React.useState(false);
    const sidebarRef = React.useRef<HTMLDivElement>(null);

    const handleMouseDown = (e: React.MouseEvent) => {
        e.preventDefault();
        setIsResizing(true);
    };

    React.useEffect(() => {
        const handleMouseMove = (e: MouseEvent) => {
            if (!isResizing) return;

            let newWidth;
            if (side === "left") {
                newWidth = e.clientX;
            } else {
                newWidth = window.innerWidth - e.clientX;
            }

            if (newWidth < minWidth) newWidth = minWidth;
            if (newWidth > maxWidth) newWidth = maxWidth;

            onResize(newWidth);
        };

        const handleMouseUp = () => {
            setIsResizing(false);
        };

        if (isResizing) {
            document.addEventListener("mousemove", handleMouseMove);
            document.addEventListener("mouseup", handleMouseUp);
        }

        return () => {
            document.removeEventListener("mousemove", handleMouseMove);
            document.removeEventListener("mouseup", handleMouseUp);
        };
    }, [isResizing, onResize, side, minWidth, maxWidth]);

    if (!isOpen) return null;

    return (
        <div
            ref={sidebarRef}
            className={cn(
                "absolute top-0 bottom-0 z-10 flex bg-background/95 backdrop-blur supports-backdrop-filter:bg-background/60 border-border shadow-lg transition-all duration-0",
                side === "left" ? "left-0 border-r" : "right-0 border-l",
                className
            )}
            style={{ width }}
            {...props}
        >
            {side === "right" && (
                <div
                    className="absolute left-0 top-0 bottom-0 w-1 cursor-col-resize hover:bg-primary/50 active:bg-primary transition-colors z-20 flex items-center justify-center -translate-x-1/2"
                    onMouseDown={handleMouseDown}
                >
                    <div className="h-8 w-1 rounded-full bg-border" />
                </div>
            )}

            <div className="flex-1 overflow-hidden h-full w-full">
                {children}
            </div>

            {side === "left" && (
                <div
                    className="absolute right-0 top-0 bottom-0 w-1 cursor-col-resize hover:bg-primary/50 active:bg-primary transition-colors z-20 flex items-center justify-center translate-x-1/2"
                    onMouseDown={handleMouseDown}
                >
                    <div className="h-8 w-1 rounded-full bg-border" />
                </div>
            )}
        </div>
    );
}
