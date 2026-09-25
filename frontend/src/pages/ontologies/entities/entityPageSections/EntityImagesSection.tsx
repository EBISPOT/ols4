import { Fragment, useEffect, useState } from "react";
import { sortByKeys } from "../../../../app/util";
import Image3D from "../../../../components/Image3D";
import Entity from "../../../../model/Entity";
import Ontology from "../../../../model/Ontology";
import LinkedEntities from "../../../../model/LinkedEntities";
import Reified from "../../../../model/Reified";
import MetadataTooltip from "./MetadataTooltip";

export default function EntityImagesSection({
  entity,
  linkedEntities,
  lightbox,
  hideHeading,
}: {
  entity: Entity | Ontology;
  linkedEntities: LinkedEntities;
  // clicking a thumbnail opens the image in an overlay instead of a new tab
  lightbox?: boolean;
  hideHeading?: boolean;
}) {
  let images = entity.getDepictedBy();
  const [lightboxSrc, setLightboxSrc] = useState<string | null>(null);

  useEffect(() => {
    if (!lightboxSrc) return;
    const onKeyDown = (e: KeyboardEvent) => {
      if (e.key === "Escape") setLightboxSrc(null);
    };
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, [lightboxSrc]);

  if (!images || images.length === 0) {
    return <Fragment />;
  }

  const imgFile =
    /.*\.(apng|avif|gif|jpg|jpeg|jfif|pjpeg|pjp|png|svg|webp|bmp|ico|cur|tif|tiff)$/g;
  const imgFile3D = /.*\.(glb|gltf)$/g;

  return (
    <div className="flex flex-col gap-1 mb-2">
      {!hideHeading && <div className="font-bold">Depicted by</div>}
      <div className="flex flex-row">
        {images
          .map((img: Reified<string>) => {
            const hasMetadata = img.hasMetadata();
            const thumbnail = (
              <img
                src={img.value}
                alt={img.value.substring(img.value.lastIndexOf("/") + 1)}
                className="rounded-lg mx-auto object-contain"
                style={{ maxWidth: "300px", minWidth: "300px" }}
              />
            );
            return (
              <div key={img.value} className="relative">
                {img.value.toLowerCase().match(imgFile3D)?.length === 1 ? (
                  <Image3D src={img.value} />
                ) : lightbox ? (
                  <button
                    type="button"
                    className="cursor-zoom-in"
                    title="Click to enlarge"
                    onClick={() => setLightboxSrc(img.value)}
                  >
                    {thumbnail}
                  </button>
                ) : (
                  <a target="_blank" href={img.value}>
                    {thumbnail}
                  </a>
                )}
                {hasMetadata && (
                  <div className="absolute top-2 right-2 bg-white rounded-full p-1 shadow-md">
                    <MetadataTooltip
                      metadata={img.getMetadata()}
                      linkedEntities={linkedEntities}
                    />
                  </div>
                )}
              </div>
            );
          })
          .sort((a, b) => sortByKeys(a, b))}
      </div>
      {lightboxSrc && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center p-8 cursor-zoom-out"
          style={{ backgroundColor: "rgba(0, 0, 0, 0.7)" }}
          onClick={() => setLightboxSrc(null)}
        >
          <img
            src={lightboxSrc}
            alt={lightboxSrc.substring(lightboxSrc.lastIndexOf("/") + 1)}
            className="rounded-lg bg-white p-2"
            style={{ maxWidth: "90vw", maxHeight: "90vh" }}
          />
        </div>
      )}
    </div>
  );
}
