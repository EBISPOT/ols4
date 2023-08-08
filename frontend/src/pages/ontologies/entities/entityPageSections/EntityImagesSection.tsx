import { Fragment } from "react";
import { randomString, sortByKeys } from "../../../../app/util";
import Entity from "../../../../model/Entity";
import LinkedEntities from "../../../../model/LinkedEntities";
import Reified from "../../../../model/Reified";
import MetadataTooltip from "./MetadataTooltip";
import Image3D from "../../../../components/Image3D";

export default function EntityImagesSection({
  entity,
  linkedEntities,
}: {
  entity: Entity;
  linkedEntities: LinkedEntities;
}) {
  let images = entity.getDepictedBy();

  if (!images || images.length === 0) {
    return <Fragment />;
  }

  const imgFile =
    /.*\.(apng|avif|gif|jpg|jpeg|jfif|pjpeg|pjp|png|svg|webp|bmp|ico|cur|tif|tiff)$/g;
  const imgFile3D =
    /.*\.(glb|gltf)$/g;

  return (
    <div>
      <div className="font-bold">Depicted by</div>
      <br/>
      <div className="flex flex-row">
      {images
        .map((img: Reified<string>) => {
          return (
            <div key={img.value}>
            {img.value.toLowerCase().match(imgFile3D)?.length === 1 ? (
		<Image3D src={img.value}/>
	    ) :  <a target="_blank" href={img.value}><img
	    src={img.value}
	    alt={img.value.substring(img.value.lastIndexOf("/") + 1)}
	    className="rounded-lg mx-auto object-contain"
	    style={{maxWidth:'300px'}}
	  /></a>}
            </div>
          );
        })
        .sort((a, b) => sortByKeys(a, b))}
	</div>
    </div>
  );
}
