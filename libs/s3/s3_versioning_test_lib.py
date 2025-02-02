#!/usr/bin/python
# -*- coding: utf-8 -*-
#
# Copyright (c) 2022 Seagate Technology LLC and/or its Affiliates
#
# This program is free software: you can redistribute it and/or modify
# it under the terms of the GNU Affero General Public License as published
# by the Free Software Foundation, either version 3 of the License, or
# (at your option) any later version.
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
# GNU Affero General Public License for more details.
# You should have received a copy of the GNU Affero General Public License
# along with this program. If not, see <https://www.gnu.org/licenses/>.
#
# For any questions about this software or licensing,
# please email opensource@seagate.com or cortx-questions@seagate.com.
#

"""Python Test Library using boto3 module to perform Object Versioning Operations."""

import logging

from botocore.exceptions import ClientError
from commons import errorcodes as err
from commons.exceptions import CTException

from config.s3 import S3_CFG
from commons.utils import assert_utils
from libs.s3 import ACCESS_KEY
from libs.s3 import SECRET_KEY
from libs.s3.s3_versioning import Versioning

LOGGER = logging.getLogger(__name__)


class S3VersioningTestLib(Versioning):
    """Class initialising s3 connection and including methods for versioning operations."""

    def __init__(self, access_key: str = ACCESS_KEY, secret_key: str = SECRET_KEY,
                 endpoint_url: str = S3_CFG["s3_url"],
                 s3_cert_path: str = S3_CFG["s3_cert_path"],
                 **kwargs) -> None:
        """
        This method initializes members of S3VersioningTestLib and its parent class.

        :param access_key: access key.
        :param secret_key: secret key.
        :param endpoint_url: endpoint url.
        :param s3_cert_path: s3 certificate path.
        :param region: region.
        :param aws_session_token: aws_session_token.
        :param debug: debug mode.
        """
        kwargs["region"] = kwargs.get("region", S3_CFG["region"])
        kwargs["aws_session_token"] = kwargs.get("aws_session_token", None)
        kwargs["debug"] = kwargs.get("debug", S3_CFG["debug"])
        super().__init__(access_key, secret_key, endpoint_url, s3_cert_path, **kwargs)

    def put_bucket_versioning(self, bucket_name: str = None, status: str = "Enabled") -> tuple:
        """
        Set/Update the versioning configuration of a bucket.

        :param bucket_name: Target bucket for the PUT Bucket Versioning call.
        :param status: Versioning status to be set, supported values - "Enabled" or "Suspended"
            Default = "Enabled"
        :return: response
        """
        LOGGER.info("Setting bucket versioning configuration")
        try:
            response = super().put_bucket_versioning(bucket_name=bucket_name, status=status)
            LOGGER.info("Successfully set bucket versioning configuration: %s", response)
        except (ClientError, Exception) as error:
            LOGGER.error("Error in %s: %s", S3VersioningTestLib.put_bucket_versioning.__name__,
                         error)
            raise CTException(err.S3_CLIENT_ERROR, error.args[0]) from error

        return True, response

    def get_bucket_versioning(self, bucket_name: str = None) -> tuple:
        """
        Get the versioning configuration of a bucket.

        :param bucket_name: Target bucket for the GET Bucket Versioning call.
        :return: response
        """
        LOGGER.info("Fetching bucket versioning configuration")
        try:
            response = super().get_bucket_versioning(bucket_name=bucket_name)
            LOGGER.info("Successfully fetched bucket versioning configuration: %s", response)
        except (ClientError, Exception) as error:
            LOGGER.error("Error in %s: %s", S3VersioningTestLib.get_bucket_versioning.__name__,
                         error)
            raise CTException(err.S3_CLIENT_ERROR, error.args[0]) from error

        return True, response

    def list_object_versions(self, bucket_name: str = None, **kwargs) -> tuple:
        """
        List all the versions and delete markers present in a bucket.

        :param bucket_name: Target bucket for the List Object Versions call.
        :param kwargs: Optional query args that can be supplied to the List Object Versions call
            delimiter, encoding_type, key_marker, max_keys, prefix, version_id_marker
        :return: response
        """
        optional_params = dict()
        user_params = list(kwargs.keys())
        if "delimiter" in user_params:
            optional_params["Delimiter"] = kwargs["delimiter"]
        if "encoding_type" in user_params:
            optional_params["EncodingType"] = kwargs["encoding_type"]
        if "key_marker" in user_params:
            optional_params["KeyMarker"] = kwargs["key_marker"]
        if "max_keys" in user_params:
            optional_params["MaxKeys"] = kwargs["max_keys"]
        if "prefix" in user_params:
            optional_params["Prefix"] = kwargs["prefix"]
        if "version_id_marker" in user_params:
            optional_params["VersionIdMarker"] = kwargs["version_id_marker"]
        LOGGER.info("Fetching bucket object versions list")
        try:
            response = super().list_object_versions(bucket_name=bucket_name,
                                                    optional_params=optional_params)
            LOGGER.info("Successfully fetched bucket object versions list: %s", response)
        except (ClientError, Exception) as error:
            LOGGER.error("Error in %s: %s", S3VersioningTestLib.list_object_versions.__name__,
                         error)
            raise CTException(err.S3_CLIENT_ERROR, error.args[0]) from error

        return True, response

    def get_object_version(self, bucket: str = None, key: str = None,
                           version_id: str = None) -> tuple:
        """
        Get a version of an object.

        :param bucket: Target bucket for GET Object with VersionId call.
        :param key: Target key for GET Object with VersionId call.
        :param version_id: Target version ID for GET Object with VersionId call.
        :return: (Boolean, response)
        """
        LOGGER.info("Getting the version of the object")
        try:
            response = super().get_object_version(bucket=bucket, key=key, version_id=version_id)
            LOGGER.info("Successfully retrieved the version of the object: %s", response)
        except (ClientError, Exception) as error:
            LOGGER.error("Error in %s: %s", S3VersioningTestLib.get_object_version.__name__,
                         error)
            raise CTException(err.S3_CLIENT_ERROR, error.args[0]) from error

        return True, response

    def head_object_version(self, bucket: str = None, key: str = None,
                            version_id: str = None) -> tuple:
        """
        Get the metadata of an object's version.

        :param bucket: Target bucket for HEAD Object with VersionId call.
        :param key: Target key for HEAD Object with VersionId call.
        :param version_id: Target version ID for HEAD Object with VersionId call.
        :return: (Boolean, response)
        """
        LOGGER.info("Getting the metadata of the object's version")
        try:
            response = super().head_object_version(bucket=bucket, key=key, version_id=version_id)
            LOGGER.info("Successfully retrieved object version's metadata: %s", response)
        except (ClientError, Exception) as error:
            LOGGER.error("Error in %s: %s", S3VersioningTestLib.head_object_version.__name__,
                         error)
            raise CTException(err.S3_CLIENT_ERROR, error.args[0]) from error

        return True, response

    def delete_object_version(self, bucket: str = None, key: str = None,
                              version_id: str = None) -> tuple:
        """
        Delete an object's version

        :param bucket: Target bucket for DELETE Object with VersionId call.
        :param key: Target key for DELETE Object with VersionId call.
        :param version_id: Target version ID for DELETE Object with VersionId call.
        :return: (Boolean, response)
        """
        LOGGER.info("Deleting the object's version")
        try:
            response = super().delete_object_version(bucket=bucket, key=key, version_id=version_id)
            LOGGER.info("Successfully deleted the object's version: %s", response)
        except (ClientError, Exception) as error:
            LOGGER.error("Error in %s: %s", S3VersioningTestLib.delete_object_version.__name__,
                         error)
            raise CTException(err.S3_CLIENT_ERROR, error.args[0]) from error

        return True, response

